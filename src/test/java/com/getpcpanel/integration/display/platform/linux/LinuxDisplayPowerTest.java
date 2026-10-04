package com.getpcpanel.integration.display.platform.linux;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.getpcpanel.integration.display.DisplayPower.DisplayInfo;
import com.getpcpanel.util.os.FakeProcess;
import com.getpcpanel.util.os.ProcessHelper;

class LinuxDisplayPowerTest {
    private static final String ON = "VCP code 0xd6 (Power mode                    ): DPM: On,  DPMS: Off (sl=0x01)\n";
    private static final String OFF = "VCP code 0xd6 (Power mode                    ): DPM: Off, DPMS: Off (sl=0x04)\n";

    @TempDir Path dir;
    private List<String> issued;
    /** Canned answers by command line; anything else exits 1 without output, as ddcutil does for a display it cannot reach. */
    private Map<String, String> answers;
    private LinuxDisplayPower sut;

    @BeforeEach
    void setUp() {
        issued = Collections.synchronizedList(new ArrayList<>());
        answers = new HashMap<>();
        sut = new LinuxDisplayPower();
        sut.warmer = r -> {
        };
        sut.processes = new ProcessHelper() {
            @Override
            public ProcessBuilder builder(String... command) {
                var line = String.join(" ", command);
                issued.add(line);
                var answer = answers.get(line);
                return super.builder(answer == null ? FakeProcess.command("fail", "1", "") : FakeProcess.command("print-file", answer));
            }
        };
    }

    @Test
    void x11TriesKdeThenXset() {
        assertEquals(List.of("kscreen-doctor", "xset"), tools(Map.of("XDG_SESSION_TYPE", "x11", "DISPLAY", ":0")));
    }

    @Test
    void waylandSkipsXsetWhichOnlyReachesXwayland() {
        assertEquals(List.of("kscreen-doctor"), tools(Map.of("XDG_SESSION_TYPE", "wayland")));
        assertEquals(List.of("kscreen-doctor"), tools(Map.of("WAYLAND_DISPLAY", "wayland-0", "DISPLAY", ":0")));
    }

    @Test
    void aDisplayThatIsOnIsTurnedOff() throws IOException {
        answer("ddcutil getvcp d6 --bus 3", ON);
        answer("ddcutil capabilities --bus 3 --verbose", "Unparsed capabilities string: (prot(monitor)vcp(02 10 D6(01 05)))\n");
        answer("ddcutil setvcp d6 5 --bus 3", "");

        sut.toggle(List.of("3"));

        assertEquals("ddcutil setvcp d6 5 --bus 3", issued.getLast());
    }

    @Test
    void withoutReadableCapabilitiesItUsesStandbyOff() throws IOException {
        answer("ddcutil getvcp d6 --bus 3", ON);
        answer("ddcutil setvcp d6 4 --bus 3", "");

        sut.toggle(List.of("3"));

        assertEquals("ddcutil setvcp d6 4 --bus 3", issued.getLast());
    }

    @Test
    void aDisplayThatIsOffIsTurnedOn() throws IOException {
        answer("ddcutil getvcp d6 --bus 3", OFF);
        answer("ddcutil setvcp d6 1 --bus 3", "");

        sut.toggle(List.of("3"));

        assertEquals(List.of("ddcutil getvcp d6 --bus 3", "ddcutil setvcp d6 1 --bus 3"), issued);
    }

    @Test
    void displaysMoveTogetherWhileAnyIsOn() throws IOException {
        answer("ddcutil getvcp d6 --bus 3", OFF);
        answer("ddcutil getvcp d6 --bus 5", ON);

        sut.toggle(List.of("3", "5"));

        assertEquals(List.of("ddcutil getvcp d6 --bus 3", "ddcutil getvcp d6 --bus 5",
                "ddcutil capabilities --bus 5 --verbose", "ddcutil setvcp d6 4 --bus 5"), issued, "the one already off stays off");
    }

    @Test
    void anUnreachableDisplayIsSkipped() throws IOException {
        answer("ddcutil getvcp d6 --bus 5", OFF);

        sut.toggle(List.of("3", "5"));

        assertEquals(List.of("ddcutil getvcp d6 --bus 3", "ddcutil getvcp d6 --bus 5", "ddcutil setvcp d6 1 --bus 5"), issued);
    }

    @Test
    void aMonitorThatAlwaysAnswersZeroGoesByWhatWasLastSet() throws IOException {
        answer("ddcutil getvcp d6 --bus 3", "VCP code 0xd6 (Power mode                    ): Invalid value (sl=0x00)\n");
        answer("ddcutil setvcp d6 4 --bus 3", "");
        answer("ddcutil setvcp d6 1 --bus 3", "");

        sut.toggle(List.of("3"));
        assertEquals("ddcutil setvcp d6 4 --bus 3", issued.getLast(), "unknown at first: taken as on, since it answers");
        sut.toggle(List.of("3"));
        assertEquals("ddcutil setvcp d6 1 --bus 3", issued.getLast());
    }

    @Test
    void aMonitorThatWentSilentWhenTurnedOffIsStillTurnedOn() throws IOException {
        answer("ddcutil getvcp d6 --bus 3", ON);
        answer("ddcutil setvcp d6 4 --bus 3", "");
        sut.toggle(List.of("3"));

        answers.remove("ddcutil getvcp d6 --bus 3");
        issued.clear();
        sut.toggle(List.of("3"));

        assertEquals(List.of("ddcutil getvcp d6 --bus 3", "ddcutil setvcp d6 1 --bus 3"), issued);
    }

    @Test
    void capabilitiesAreReadOncePerDisplay() throws IOException {
        answer("ddcutil getvcp d6 --bus 3", ON);
        answer("ddcutil capabilities --bus 3 --verbose", "Unparsed capabilities string: (vcp(D6(01 05)))\n");

        sut.toggle(List.of("3"));
        sut.toggle(List.of("3"));

        assertEquals(1, issued.stream().filter(c -> c.contains("capabilities")).count());
    }

    @Test
    void aFailedCapabilitiesReadIsTriedAgain() throws IOException {
        answer("ddcutil getvcp d6 --bus 3", ON);

        sut.toggle(List.of("3"));
        answer("ddcutil capabilities --bus 3 --verbose", "Unparsed capabilities string: (vcp(D6(01 05)))\n");
        answer("ddcutil setvcp d6 5 --bus 3", "");
        sut.toggle(List.of("3"));

        assertEquals(2, issued.stream().filter(c -> c.contains("capabilities")).count());
        assertEquals("ddcutil setvcp d6 5 --bus 3", issued.getLast());
    }

    @Test
    void listingReadsTheCapabilitiesAheadOfAPress() throws IOException {
        var warmups = new ArrayList<Runnable>();
        sut.warmer = warmups::add;
        answer("ddcutil detect --brief", "Display 1\n   I2C bus:          /dev/i2c-3\n");
        answer("ddcutil capabilities --bus 3 --verbose", "Unparsed capabilities string: (vcp(D6(01 05)))\n");
        answer("ddcutil getvcp d6 --bus 3", ON);
        answer("ddcutil setvcp d6 5 --bus 3", "");

        sut.list();
        assertEquals(List.of("ddcutil detect --brief"), issued, "the capabilities are read off the caller's thread");
        warmups.forEach(Runnable::run);
        issued.clear();
        sut.toggle(List.of("3"));

        assertEquals(List.of("ddcutil getvcp d6 --bus 3", "ddcutil setvcp d6 5 --bus 3"), issued);
    }

    @Test
    void onlyOneCapabilityReadRunsAtATime() throws IOException {
        var warmups = new ArrayList<Runnable>();
        sut.warmer = warmups::add;
        answer("ddcutil detect --brief", "Display 1\n   I2C bus:          /dev/i2c-3\n");
        answer("ddcutil capabilities --bus 3 --verbose", "Unparsed capabilities string: (vcp(D6(01 05)))\n");

        sut.list();
        sut.list();
        assertEquals(1, warmups.size(), "the second listing finds one already running");

        warmups.getFirst().run();
        sut.list();
        assertEquals(1, warmups.size(), "nothing left to read");
        assertEquals(1, issued.stream().filter(c -> c.contains("capabilities")).count());
    }

    @Test
    void aDisplayWhoseCapabilitiesFailedIsNotAskedAgainOnListing() throws IOException {
        var warmups = new ArrayList<Runnable>();
        sut.warmer = warmups::add;
        answer("ddcutil detect --brief", "Display 1\n   I2C bus:          /dev/i2c-3\n");

        sut.list();
        warmups.removeFirst().run();
        sut.list();
        assertEquals(List.of(), warmups, "a laptop panel or a monitor with DDC/CI off is not probed after every save");
        assertEquals(1, issued.stream().filter(c -> c.contains("capabilities")).count());

        answer("ddcutil getvcp d6 --bus 3", ON);
        sut.toggle(List.of("3"));
        assertEquals(2, issued.stream().filter(c -> c.contains("capabilities")).count(), "a press still tries");
    }

    @Test
    void listsWhatDdcutilDetects() throws IOException {
        answer("ddcutil detect --brief", """
                Display 1
                   I2C bus:          /dev/i2c-3
                   DRM connector:    card1-DP-1
                   Monitor:          GSM:LG HDR WQHD:

                Invalid display
                   I2C bus:          /dev/i2c-4
                   DRM connector:    card1-eDP-1

                Display 2
                   I2C bus:          /dev/i2c-6
                   DRM connector:    card1-HDMI-A-1
                   Monitor:          DEL::
                """);

        assertEquals(List.of(new DisplayInfo("3", "LG HDR WQHD · Display 1"), new DisplayInfo("6", "Display 2")), sut.list());
    }

    @Test
    void takesTheModelFromAFullDetect() {
        var displays = LinuxDisplayPower.parseDetect(List.of(
                "Display 3",
                "   I2C bus:  /dev/i2c-7",
                "   EDID synopsis:",
                "      Mfg id:               DEL - Dell Inc.",
                "      Model:                DELL S3221QS",
                "      Product code:         53509  (0xd105)"));

        assertEquals(List.of(new DisplayInfo("7", "DELL S3221QS · Display 3")), displays);
    }

    @Test
    void listsNothingWithoutDdcutil() {
        assertEquals(List.of(), sut.list());
    }

    private void answer(String commandLine, String output) throws IOException {
        var file = Files.createTempFile(dir, "answer", ".txt");
        Files.writeString(file, output);
        answers.put(commandLine, file.toString());
    }

    private static List<String> tools(Map<String, String> env) {
        return LinuxDisplayPower.commands(env).stream().map(c -> c[0]).toList();
    }
}
