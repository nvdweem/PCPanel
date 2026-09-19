package com.getpcpanel.integration.volume.platform.linux;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.getpcpanel.integration.volume.platform.MuteType;
import com.getpcpanel.integration.volume.platform.linux.PulseAudioWrapper.InOutput;
import com.getpcpanel.integration.volume.platform.linux.PulseAudioWrapper.PactlTimeoutException;
import com.getpcpanel.util.os.FakeProcess;
import com.getpcpanel.util.os.ProcessHelper;

class PulseAudioWrapperTest {
    /** Generous upper bound for one call: the deadline itself plus starting a JVM for the fake pactl. */
    private static final Duration CALL_BUDGET = Duration.ofSeconds(20);

    /** A wrapper whose every pactl invocation runs {@link FakeProcess} with {@code args} instead. */
    private static PulseAudioWrapper wrapperRunning(String... args) {
        var sut = new PulseAudioWrapper();
        sut.processHelper = new ProcessHelper() {
            @Override
            public ProcessBuilder builder(String... command) {
                return super.builder(FakeProcess.command(args));
            }
        };
        sut.timeoutMillis = 2_000;
        return sut;
    }

    private static Set<Long> aliveChildren() {
        return ProcessHandle.current().children().filter(ProcessHandle::isAlive).map(ProcessHandle::pid).collect(Collectors.toSet());
    }

    /** A pactl that never returns (audio server wedged mid-restart) must not block the reader forever. */
    @Test
    void listGivesUpOnAHungPactl() {
        var sut = wrapperRunning("hang");
        var before = aliveChildren();

        assertTimeoutPreemptively(CALL_BUDGET, () -> assertThrows(PactlTimeoutException.class, sut::getSessions));
        assertEquals(before, aliveChildren(), "the hung pactl must be killed, not leaked");
    }

    /** A volume write to a hung pactl must return (so the command thread keeps serving knobs) and kill it. */
    @Test
    void writeGivesUpOnAHungPactl() {
        var sut = wrapperRunning("hang");
        var before = aliveChildren();

        assertTimeoutPreemptively(CALL_BUDGET, () -> assertDoesNotThrow(() -> sut.setSessionVolume(0, 0.5f)));
        assertEquals(before, aliveChildren(), "the hung pactl must be killed, not leaked");
    }

    /**
     * A write returns only once pactl has finished, so consecutive knob values are applied one at a time and in
     * order - a later value can't be overtaken by an earlier pactl that happened to connect slower.
     */
    @Test
    void writeWaitsForPactlToFinish(@TempDir Path dir) {
        var marker = dir.resolve("done");
        var sut = wrapperRunning("slow", "700", marker.toString());

        assertTimeoutPreemptively(CALL_BUDGET, () -> sut.setSessionVolume(0, 0.5f));
        assertTrue(Files.exists(marker), "setSessionVolume returned before pactl finished");
    }

    /** Output far larger than a pipe buffer must be read fully while waiting, not deadlock the deadline. */
    @Test
    void readsListOutputLargerThanThePipeBuffer() {
        var sut = wrapperRunning("sinks", "3000");

        var sinks = assertTimeoutPreemptively(CALL_BUDGET, () -> sut.execAndParse(InOutput.output));
        assertEquals(3000, sinks.size());
        assertEquals("sink-2999", sinks.getLast().metas().get("Name"));
    }

    /**
     * Regression for the Linux native build crashing on startup with "Cannot run program pactl": a missing
     * pactl (no PulseAudio/PipeWire - a headless box or a CI runner without pulseaudio-utils) must degrade to
     * a no-op ISndCtrl, not abort. Reads return empty (so startup's device/session enumeration succeeds and
     * the app serves the UI) and writes don't throw.
     */
    @Test
    void degradesToNoOpWhenPactlIsUnavailable() {
        var sut = new PulseAudioWrapper();
        // Redirect every invocation to a guaranteed-missing binary so start() throws "command not found",
        // exactly as a real machine without pactl would.
        sut.processHelper = new ProcessHelper() {
            @Override
            public ProcessBuilder builder(String... command) {
                return super.builder("pcpanel-no-such-binary");
            }
        };

        assertEquals(List.of(), sut.devices(), "no devices should be reported when pactl is unavailable");
        assertEquals(List.of(), sut.getSessions(), "no sessions should be reported when pactl is unavailable");
        assertDoesNotThrow(() -> sut.setSessionVolume(0, 0.5f), "a volume write must not throw");
        assertDoesNotThrow(() -> sut.setDeviceVolume(true, 0, 0.5f), "a device volume write must not throw");
        assertDoesNotThrow(() -> sut.muteSession(0, MuteType.mute), "a mute write must not throw");
        assertDoesNotThrow(() -> sut.setDefaultDevice(true, 0), "setting the default device must not throw");
    }
}
