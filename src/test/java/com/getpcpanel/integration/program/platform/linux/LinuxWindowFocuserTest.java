package com.getpcpanel.integration.program.platform.linux;

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

import com.getpcpanel.integration.program.WindowFocuser.Result;
import com.getpcpanel.platform.process.LinuxProcessHelper.WindowTool;
import com.getpcpanel.platform.process.LinuxProcessHelper.WindowTool.Kind;
import com.getpcpanel.util.os.FakeProcess;
import com.getpcpanel.util.os.ProcessHelper;

class LinuxWindowFocuserTest {
    private static final WindowTool XDOTOOL = new WindowTool(Kind.XDOTOOL, "xdotool");
    private static final WindowTool KDOTOOL = new WindowTool(Kind.KDOTOOL, "/opt/pcpanel/kdotool");
    private static final WindowTool HYPRCTL = new WindowTool(Kind.HYPRCTL, "hyprctl");

    @TempDir Path dir;
    private List<String> issued;
    /** Canned answers by command line; anything else exits 1 without output, as a tool that found nothing does. */
    private Map<String, String> answers;
    private LinuxWindowFocuser sut;

    @BeforeEach
    void setUp() {
        issued = Collections.synchronizedList(new ArrayList<>());
        answers = new HashMap<>();
        sut = new LinuxWindowFocuser();
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
    void xdotoolActivatesTheFirstWindowOfTheApp() throws IOException {
        answer("xdotool search --class ^[sS][pP][oO][tT][iI][fF][yY]$", "62914561\n62914562\n");
        answer("xdotool windowactivate 62914561", "");

        assertEquals(Result.FOCUSED, sut.focusOrMinimize(XDOTOOL, "Spotify.exe", false));
        assertEquals(List.of("xdotool search --class ^[sS][pP][oO][tT][iI][fF][yY]$", "xdotool windowactivate 62914561"), issued);
    }

    @Test
    void anEmptySearchMeansTheAppIsNotRunning() {
        assertEquals(Result.NOT_RUNNING, sut.focusOrMinimize(XDOTOOL, "spotify", false));
        assertEquals(List.of("xdotool search --class ^[sS][pP][oO][tT][iI][fF][yY]$", "xdotool search --class [sS][pP][oO][tT][iI][fF][yY]"), issued);
    }

    @Test
    void aClassThatOnlyContainsTheNameCountsWhenNoneIsExact() throws IOException {
        answer("xdotool search --class [cC][hH][rR][oO][mM][eE]", "7\n");
        answer("xdotool windowactivate 7", "");

        assertEquals(Result.FOCUSED, sut.focusOrMinimize(XDOTOOL, "chrome", false));
        assertEquals(List.of("xdotool search --class ^[cC][hH][rR][oO][mM][eE]$", "xdotool search --class [cC][hH][rR][oO][mM][eE]",
                "xdotool windowactivate 7"), issued);
    }

    @Test
    void aWindowThatWontActivateIsSkipped() throws IOException {
        answer("xdotool search --class ^[sS][pP][oO][tT][iI][fF][yY]$", "1\n2\n");
        answer("xdotool windowactivate 2", "");

        assertEquals(Result.FOCUSED, sut.focusOrMinimize(XDOTOOL, "spotify", false));
        assertEquals(List.of("xdotool search --class ^[sS][pP][oO][tT][iI][fF][yY]$", "xdotool windowactivate 1", "xdotool windowactivate 2"), issued);
    }

    @Test
    void theAppInFrontIsMinimisedWhenAsked() throws IOException {
        answer("xdotool search --class ^[sS][pP][oO][tT][iI][fF][yY]$", "1\n2\n");
        answer("xdotool getactivewindow", "2\n");
        answer("xdotool windowminimize 2", "");

        assertEquals(Result.MINIMIZED, sut.focusOrMinimize(XDOTOOL, "spotify", true));
        assertEquals(List.of("xdotool search --class ^[sS][pP][oO][tT][iI][fF][yY]$", "xdotool getactivewindow", "xdotool windowminimize 2"), issued);
    }

    @Test
    void anAppBehindAnotherIsBroughtToTheFrontEvenWhenMinimisingIsOn() throws IOException {
        answer("xdotool search --class ^[sS][pP][oO][tT][iI][fF][yY]$", "1\n");
        answer("xdotool getactivewindow", "9\n");
        answer("xdotool windowactivate 1", "");

        assertEquals(Result.FOCUSED, sut.focusOrMinimize(XDOTOOL, "spotify", true));
    }

    @Test
    void kdotoolIsDrivenTheSameWay() throws IOException {
        answer("/opt/pcpanel/kdotool search --class ^[sS][pP][oO][tT][iI][fF][yY]$", "{a1}\n");
        answer("/opt/pcpanel/kdotool windowactivate {a1}", "");

        assertEquals(Result.FOCUSED, sut.focusOrMinimize(KDOTOOL, "spotify", false));
    }

    @Test
    void regexCharactersInTheNameAreMatchedLiterally() {
        sut.focusOrMinimize(XDOTOOL, "/usr/bin/my.app+", false);

        assertEquals(List.of("xdotool search --class ^[mM][yY]\\.[aA][pP][pP]\\+$", "xdotool search --class [mM][yY]\\.[aA][pP][pP]\\+"), issued);
    }

    @Test
    void hyprlandFocusesByClass() throws IOException {
        answer("hyprctl dispatch focuswindow class:(?i)^spotify$", "ok\n");

        assertEquals(Result.FOCUSED, sut.focusOrMinimize(HYPRCTL, "spotify", false));
    }

    @Test
    void hyprlandFallsBackToAClassContainingTheName() throws IOException {
        answer("hyprctl dispatch focuswindow class:(?i)chrome", "ok\n");

        assertEquals(Result.FOCUSED, sut.focusOrMinimize(HYPRCTL, "chrome", false));
        assertEquals(List.of("hyprctl dispatch focuswindow class:(?i)^chrome$", "hyprctl dispatch focuswindow class:(?i)chrome"), issued);
    }

    @Test
    void hyprlandWithoutAMatchingWindowIsNotRunning() throws IOException {
        answer("hyprctl dispatch focuswindow class:(?i)^spotify$", "No such window found\n");

        assertEquals(Result.NOT_RUNNING, sut.focusOrMinimize(HYPRCTL, "spotify", false));
    }

    private void answer(String commandLine, String output) throws IOException {
        var file = Files.writeString(dir.resolve("answer-" + answers.size()), output);
        answers.put(commandLine, file.toString());
    }
}
