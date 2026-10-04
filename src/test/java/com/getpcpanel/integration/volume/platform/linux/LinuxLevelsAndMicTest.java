package com.getpcpanel.integration.volume.platform.linux;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
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
        var buffer = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putFloat(0.1f).putFloat(-0.7f).putFloat(0.3f).putFloat(1.5f);
        var bytes = buffer.array();

        assertEquals(0.7f, LinuxAudioLevelMeter.loudest(bytes, 12), 1e-6);
        assertEquals(1f, LinuxAudioLevelMeter.loudest(bytes, 16), "clipped samples count as full scale");
        assertEquals(0.1f, LinuxAudioLevelMeter.loudest(bytes, 7), 1e-6, "a partial sample is left out");
    }

    @Test
    void recordingsCarryTheMeterName() {
        var command = LinuxAudioLevelMeter.command("--monitor-stream=12");
        assertEquals("parec", command.getFirst());
        assertTrue(command.contains("--monitor-stream=12"));
        assertTrue(command.contains("--client-name=" + LinuxAudioLevelMeter.CLIENT_NAME));
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
    void micUsersLeaveOutMonitorsAndOurOwnMeters() {
        var sources = List.of(
                target(InOutput.input, 1, Map.of("Name", "alsa_input.usb-mic"), Map.of()),
                target(InOutput.input, 2, Map.of("Name", "alsa_output.speakers.monitor"), Map.of()));
        var recordings = List.of(
                target(InOutput.recording, 10, Map.of("Source", "1"), Map.of("application.process.binary", "Discord")),
                target(InOutput.recording, 11, Map.of("Source", "2"), Map.of("application.process.binary", "obs")),
                target(InOutput.recording, 12, Map.of("Source", "1"), Map.of("application.name", LinuxAudioLevelMeter.CLIENT_NAME, "application.process.binary", "parec")),
                target(InOutput.recording, 13, Map.of("Source", "1"), Map.of("pipewire.access.portal.app_id", "us.zoom.Zoom")));

        assertEquals(Set.of("discord", "us.zoom.zoom"), LinuxMicUsage.appsUsingMic(recordings, sources));
    }

    private static PulseAudioTarget target(InOutput type, int index, Map<String, String> metas, Map<String, String> properties) {
        return PulseAudioTarget.builder().type(type).index(index).metas(metas).properties(properties).build();
    }
}
