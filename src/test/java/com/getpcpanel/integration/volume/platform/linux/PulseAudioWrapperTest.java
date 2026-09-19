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
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.getpcpanel.integration.volume.platform.MuteType;
import com.getpcpanel.integration.volume.platform.linux.PulseAudioWrapper.InOutput;
import com.getpcpanel.integration.volume.platform.linux.PulseAudioWrapper.PactlTimeoutException;
import com.getpcpanel.integration.volume.platform.linux.PulseAudioWrapper.PulseAudioTarget;
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

    /** {@code pactl info} as PipeWire's pulse server prints it (PipeWire 1.0.5). */
    @Test
    void readsTheDefaultsFromPipeWire() {
        var info = List.of(
                "Server String: unix:/run/user/1000/pulse/native",
                "Server Name: PulseAudio (on PipeWire 1.0.5)",
                "Server Version: 15.0.0",
                "Default Sample Specification: float32le 2ch 48000Hz",
                "Default Channel Map: front-left,front-right",
                "Default Sink: headset_b",
                "Default Source: mic_b",
                "Cookie: 2dd8:0fe8");

        assertEquals(Map.of(InOutput.output, "headset_b", InOutput.input, "mic_b"), PulseAudioWrapper.parseDefaultDeviceNames(info));
    }

    /** {@code pactl info} as PulseAudio itself prints it (17.0). */
    @Test
    void readsTheDefaultsFromPulseAudio() {
        var info = List.of(
                "Server Name: pulseaudio",
                "Server Version: 17.0-25-gc3305",
                "Default Sample Specification: s16le 2ch 44100Hz",
                "Default Channel Map: front-left,front-right",
                "Default Sink: RDPSink",
                "Default Source: RDPSource");

        assertEquals(Map.of(InOutput.output, "RDPSink", InOutput.input, "RDPSource"), PulseAudioWrapper.parseDefaultDeviceNames(info));
    }

    /** The default sink and the default source are flagged; every other device, and a same-named one of the other kind, is not. */
    @Test
    void devicesFlagTheDefaultSinkAndSource() {
        var sut = new PulseAudioWrapper() {
            @Override
            List<PulseAudioTarget> execAndParse(InOutput type) {
                return switch (type) {
                    case output -> List.of(target(1, type, "speakers"), target(2, type, "headset"));
                    case input -> List.of(target(3, type, "mic"), target(4, type, "headset"), target(5, type, "speakers.monitor"));
                    case session -> List.of();
                };
            }

            @Override
            Map<InOutput, String> defaultDeviceNames() {
                return Map.of(InOutput.output, "headset", InOutput.input, "mic");
            }
        };

        var defaults = sut.devices().stream().filter(PulseAudioTarget::isDefault).map(t -> t.type() + ":" + t.index()).toList();

        assertEquals(List.of("output:2", "input:3"), defaults);
    }

    private static PulseAudioTarget target(int index, InOutput type, String name) {
        return PulseAudioTarget.builder().index(index).type(type).metas(Map.of("Name", name)).properties(Map.of()).build();
    }
}
