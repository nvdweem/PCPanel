package com.getpcpanel.integration.volume.platform.linux;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.getpcpanel.integration.volume.platform.linux.PulseAudioWrapper.InOutput;

import dev.niels.pulse.ChannelVolumes;
import dev.niels.pulse.model.DeviceInfo;
import dev.niels.pulse.model.StreamInfo;

/** A target built from the protocol must read exactly like one parsed from {@code pactl list}. */
class NativePulseTargetsTest {
    private static DeviceInfo sink(int index, String name, long volume, boolean muted) {
        return new DeviceInfo(index, name, name + " description", new ChannelVolumes(volume, volume), muted, 9, name + ".monitor", "module-alsa-card.c",
                Map.of("device.class", "sound"), "analog-output");
    }

    @Test
    void deviceReadsLikePactl() {
        var sut = new SndCtrlPulseAudio();
        var target = NativePulseTargets.device(sink(3, "alsa_output.pci", 42174, true), InOutput.output, true);

        assertEquals(3, target.index());
        assertEquals("alsa_output.pci", target.name());
        assertEquals("alsa_output.pci description", target.metas().get("Description"));
        assertTrue(target.isDefault());
        assertTrue(SndCtrlPulseAudio.isMuted(target));
        assertEquals(PulseAudioWrapper.volumeItoF(42174), sut.extractVolume(target));
        assertEquals("sound", target.properties().get("device.class"));
    }

    @Test
    void marksOnlyTheDefaultDevice() {
        var targets = NativePulseTargets.devices(List.of(sink(0, "a", 0, false), sink(1, "b", 0, false)), InOutput.output, "b");

        assertFalse(targets.get(0).isDefault());
        assertTrue(targets.get(1).isDefault());
    }

    @Test
    void sessionReadsLikePactl() {
        var sut = new SndCtrlPulseAudio();
        var stream = new StreamInfo(12, "playback", 4, 3, new ChannelVolumes(32768, 32768), false, true, "protocol-native.c",
                Map.of("application.name", "Firefox", "application.process.binary", "firefox", "application.process.id", "1234"));

        var target = NativePulseTargets.stream(stream, InOutput.session);
        var session = sut.toSession(target);

        assertEquals("3", target.metas().get("Sink"));
        assertEquals(12, session.index());
        assertEquals("Firefox", session.title());
        assertEquals(1234, session.pid());
        assertEquals(0.5f, session.volume());
        assertFalse(session.muted());
        assertTrue(session.corked());
    }

    @Test
    void recordingCarriesItsSource() {
        var stream = new StreamInfo(90, "capture", 4, 7, null, false, false, null, Map.of("application.name", "OBS"));

        var target = NativePulseTargets.stream(stream, InOutput.recording);

        assertEquals("7", target.metas().get("Source"));
        assertFalse(target.metas().containsKey("Volume"));
    }

    @Test
    void volumeTextKeepsTheFirstChannelFirst() {
        assertEquals("0: 65536 / 100%,   1: 0 / 0%", NativePulseTargets.volume(new ChannelVolumes(65536, 0)));
    }
}
