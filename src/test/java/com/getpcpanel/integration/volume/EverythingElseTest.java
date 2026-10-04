package com.getpcpanel.integration.volume;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.getpcpanel.integration.volume.platform.AudioSession;

class EverythingElseTest {
    private static AudioSession session(String exe) {
        return new AudioSession(null, 1234, new File(exe), exe, null, 1f, false);
    }

    @Test
    void keepsOnlyAppsWithoutAControl() {
        var sessions = List.of(session("Spotify.exe"), session("Discord.exe"), session("game.exe"));

        assertEquals(Set.of("game.exe"), EverythingElse.unclaimed(sessions, List.of("spotify", "Discord.exe")));
    }

    @Test
    void nothingClaimedIsEverything() {
        assertEquals(Set.of("a.exe", "b.exe"), EverythingElse.unclaimed(List.of(session("a.exe"), session("b.exe")), List.of()));
    }

    @Test
    void recognisesTheToken() {
        assertTrue(EverythingElse.isToken("All apps without their own control"));
        assertTrue(EverythingElse.isToken(" all apps WITHOUT their own control "));
    }

    @Test
    void everythingButTheFocusedApp() {
        var sessions = List.of(session("C:/Games/game.exe"), session("Spotify.exe"), session("Discord.exe"));
        assertEquals(Set.of("Spotify.exe", "Discord.exe"), EverythingElse.allBut(sessions, new File("C:/Games/game.exe").getPath()));
        assertEquals(Set.of("game.exe", "Discord.exe"), EverythingElse.allBut(sessions, "spotify"), "matched like a focus dial does");
        assertEquals(Set.of("game.exe", "Spotify.exe", "Discord.exe"), EverythingElse.allBut(sessions, null), "nothing focused");
    }

    @Test
    void recognisesTheFocusToken() {
        assertTrue(EverythingElse.isToken("All apps except the focused one"));
        assertTrue(EverythingElse.isFocusToken(" all apps EXCEPT the focused one"));
        assertFalse(EverythingElse.isFocusToken("All apps without their own control"));
    }

    @Test
    void everyApp() {
        var sessions = List.of(session("spotify.exe"), session("firefox.exe"), new AudioSession(null, 0, new File("sys.exe"), "sys.exe", null, 1f, false));
        assertEquals(Set.of("spotify.exe", "firefox.exe"), EverythingElse.all(sessions));
        assertTrue(EverythingElse.isToken("all apps "));
        assertFalse(EverythingElse.isFocusToken("All apps"));
    }
}
