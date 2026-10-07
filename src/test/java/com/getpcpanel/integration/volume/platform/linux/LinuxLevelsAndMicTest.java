package com.getpcpanel.integration.volume.platform.linux;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.getpcpanel.integration.volume.platform.linux.PulseAudioWrapper.InOutput;
import com.getpcpanel.integration.volume.platform.linux.PulseAudioWrapper.PulseAudioTarget;

/** The parts of the Linux audio-level, mute-state and microphone support that need no running audio server. */
class LinuxLevelsAndMicTest {
    @Test
    void loudestSampleOfAChunk() {
        assertEquals(0.7f, LinuxAudioLevelMeter.loudest(FloatBuffer.wrap(new float[] { 0.1f, -0.7f, 0.3f })), 1e-6);
        assertEquals(1f, LinuxAudioLevelMeter.loudest(FloatBuffer.wrap(new float[] { 0.1f, 1.5f })), "clipped samples count as full scale");
        assertEquals(0f, LinuxAudioLevelMeter.loudest(FloatBuffer.allocate(0)));
    }

    /** parec reads whole little-endian samples; a piece that ends mid-sample leaves that sample out. */
    @Test
    void parecPiecesAreWholeSamples() {
        var bytes = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN).putFloat(0.1f).putFloat(-0.7f).array();
        var pieces = new java.util.ArrayList<Float>();
        LinuxRecorder.readParec(new java.io.ByteArrayInputStream(bytes, 0, 7), 16, f -> {
            while (f.hasRemaining()) {
                pieces.add(f.get());
            }
        });
        assertEquals(List.of(0.1f), pieces);
    }

    @Test
    void parecRecordingsCarryTheirName() {
        var command = LinuxRecorder.parecCommand(LinuxRecorder.Target.stream(12), LinuxAudioLevelMeter.CLIENT_NAME, 8000, 20);
        assertEquals("parec", command.getFirst());
        assertTrue(command.contains("--monitor-stream=12"));
        assertTrue(command.contains("--client-name=" + LinuxAudioLevelMeter.CLIENT_NAME));
        assertTrue(command.contains("--rate=8000"));
        assertTrue(command.contains("--latency-msec=20"));
    }

    @Test
    void streamReadsItsMuteAndOutput() {
        var pa = target(InOutput.session, 7, Map.of("Mute", "yes", "Sink", "47", "Volume", "front-left: 32768 /  50% / -18.06 dB"),
                Map.of("application.process.binary", "spotify"));
        var session = new SndCtrlPulseAudio().toSession(pa);
        assertTrue(session.muted());
        assertEquals(0.5f, session.volume(), 1e-4);
        assertEquals(null, session.deviceId(), "no outputs known yet");

        var unmuted = new SndCtrlPulseAudio().toSession(target(InOutput.session, 8, Map.of("Mute", "no"), Map.of()));
        assertFalse(unmuted.muted());
    }

    @Test
    void aPausedStreamIsCorked() {
        assertTrue(new SndCtrlPulseAudio().toSession(target(InOutput.session, 9, Map.of("Corked", "yes"), Map.of())).corked());
        assertFalse(new SndCtrlPulseAudio().toSession(target(InOutput.session, 9, Map.of("Corked", "no"), Map.of())).corked());
        assertFalse(new SndCtrlPulseAudio().toSession(target(InOutput.session, 9, Map.of(), Map.of())).corked(), "unknown counts as playing");
    }

    @Test
    void theVisualizerRecordsAnOutputsMonitor() {
        assertEquals(LinuxRecorder.Target.source("@DEFAULT_MONITOR@"), LinuxLoopbackCapture.target(null, false));
        assertEquals("--device=alsa_output.usb-headset.monitor", LinuxLoopbackCapture.target("alsa_output.usb-headset", false).parecArgument());
    }

    @Test
    void theVisualizerRecordsAnInputItself() {
        assertEquals("@DEFAULT_SOURCE@", LinuxLoopbackCapture.sourceName(null, true));
        assertEquals("alsa_input.usb-mic", LinuxLoopbackCapture.sourceName("alsa_input.usb-mic", true));
        assertEquals("@DEFAULT_MONITOR@", LinuxLoopbackCapture.sourceName(null, false));
        assertEquals("alsa_output.speakers.monitor", LinuxLoopbackCapture.sourceName("alsa_output.speakers", false));
    }

    @Test
    void micUsersLeaveOutMonitorsAndOurOwnMeters() {
        var sources = List.of(
                target(InOutput.input, 1, Map.of("Name", "alsa_input.usb-mic"), Map.of()),
                target(InOutput.input, 2, Map.of("Name", "alsa_output.speakers.monitor"), Map.of()));
        var recordings = List.of(
                target(InOutput.recording, 10, Map.of("Source", "1"), Map.of("application.process.binary", "Discord")),
                target(InOutput.recording, 11, Map.of("Source", "2"), Map.of("application.process.binary", "obs")),
                target(InOutput.recording, 12, Map.of("Source", "1"), Map.of("application.name", LinuxAudioLevelMeter.CLIENT_NAME, "application.process.binary", "parec")),
                target(InOutput.recording, 13, Map.of("Source", "1"), Map.of("pipewire.access.portal.app_id", "us.zoom.Zoom")),
                target(InOutput.recording, 14, Map.of("Source", "1"), Map.of("application.name", LinuxLoopbackCapture.CLIENT_NAME, "application.process.binary", "parec")));

        assertEquals(Set.of("discord", "us.zoom.zoom"), LinuxMicUsage.appsUsingMic(recordings, sources));
    }

    private static PulseAudioTarget target(InOutput type, int index, Map<String, String> metas, Map<String, String> properties) {
        return PulseAudioTarget.builder().type(type).index(index).metas(metas).properties(properties).build();
    }
}
