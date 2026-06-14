package com.getpcpanel.cpp.linux.pulseaudio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.getpcpanel.cpp.linux.pulseaudio.PulseAudioWrapper.PulseAudioTarget;

class SndCtrlPulseAudioTest {

    @CsvSource(value = {
            "0;front-left: 0 /   0% / -inf dB,   front-right: 0 /   0% / -inf dB",
            "1;mono: 65536 / 100% / 0.00 dB",
            "0.64352417;front-left: 42174 /  64% / -11.49 dB,   front-right: 42174 /  64% / -11.49 dB",
    }, delimiter = ';')
    @ParameterizedTest
    void extractVolume(float expected, String input) {
        var sut = new SndCtrlPulseAudio();

        var pa = PulseAudioTarget.builder().metas(Map.of("Volume", input)).build();
        var volume = sut.extractVolume(pa);
        assertEquals(expected, volume);
    }

    /** A regular app exposes application.name / process.binary and is matched by either. */
    @Test
    void matchesByExecutableAndName() {
        var session = session(Map.of(
                "application.process.id", "1234",
                "application.process.binary", "firefox",
                "application.name", "Firefox"));

        assertEquals("Firefox", session.title());
        assertTrue(SndCtrlPulseAudio.matches(session, "firefox"), "should match the binary name");
        assertTrue(SndCtrlPulseAudio.matches(session, "Firefox"), "should match the application name");
        assertFalse(SndCtrlPulseAudio.matches(session, "chrome"));
    }

    /** Spotify Flatpak >= 1.2.86 exposes only media.name + portal app id; it must stay targetable (#92). */
    @Test
    void matchesSpotifyByPortalAppIdWhenProcessMetadataMissing() {
        var session = session(Map.of(
                "media.name", "audio-src",
                "pipewire.access.portal.app_id", "com.spotify.Client"));

        assertEquals("com.spotify.Client", session.portalAppId());
        assertEquals("com.spotify.Client", session.title(), "portal app id should be the fallback title");
        assertTrue(SndCtrlPulseAudio.matches(session, "com.spotify.Client"), "should match the portal app id");
        assertTrue(SndCtrlPulseAudio.matches(session, "COM.SPOTIFY.CLIENT"), "match is case-insensitive");
        assertFalse(SndCtrlPulseAudio.matches(session, ""), "blank query never matches");
    }

    private static PulseAudioAudioSession session(Map<String, String> properties) {
        var pa = PulseAudioTarget.builder().properties(properties).metas(Map.of()).build();
        return new SndCtrlPulseAudio().toSession(pa);
    }
}
