package com.getpcpanel.integration.volume;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.File;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.getpcpanel.integration.volume.platform.AudioDevice;
import com.getpcpanel.integration.volume.platform.AudioSession;
import com.getpcpanel.integration.volume.platform.MuteType;

class MuteFeedbackTest {
    private static AudioSession session(String exe, String title, boolean muted) {
        return new AudioSession(null, 42, new File("C:\\Apps\\" + exe), title, null, 0.5f, muted);
    }

    @Test
    void togglingAnUnmutedAppReportsItMutedUnderItsFriendlyName() {
        var sessions = List.of(session("chrome.exe", "Chrome", false), session("Spotify.exe", "Spotify", false));

        assertEquals("Spotify \u00b7 Muted", MuteFeedback.forSessions(sessions, Set.of("spotify.exe"), MuteType.toggle));
    }

    @Test
    void togglingAMutedAppReportsItUnmuted() {
        var sessions = List.of(session("Spotify.exe", "Spotify", true));

        assertEquals("Spotify \u00b7 Unmuted", MuteFeedback.forSessions(sessions, Set.of("Spotify"), MuteType.toggle));
    }

    @Test
    void anAppNamedByItsPathMatches() {
        var sessions = List.of(session("Spotify.exe", "Spotify", false));

        assertEquals("Spotify \u00b7 Muted", MuteFeedback.forSessions(sessions, Set.of("C:\\Apps\\Spotify.exe"), MuteType.mute));
    }

    @Test
    void anAppThatIsNotPlayingReportsNothing() {
        assertNull(MuteFeedback.forSessions(List.of(session("chrome.exe", "Chrome", false)), Set.of("spotify.exe"), MuteType.toggle));
    }

    @Test
    void aDeviceReportsUnderItsName() {
        var device = new AudioDevice(null, "Headphones", "dev-1");

        assertEquals("Headphones \u00b7 Muted", MuteFeedback.forDevice(device, MuteType.toggle));
        assertEquals("Headphones \u00b7 Unmuted", MuteFeedback.forDevice(device, MuteType.unmute));
        assertNull(MuteFeedback.forDevice(null, MuteType.toggle));
    }
}
