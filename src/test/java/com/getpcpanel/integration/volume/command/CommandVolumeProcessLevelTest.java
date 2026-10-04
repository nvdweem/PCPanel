package com.getpcpanel.integration.volume.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.File;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.getpcpanel.integration.volume.platform.AudioSession;

class CommandVolumeProcessLevelTest {
    private static AudioSession session(int pid, String exe, float volume) {
        return new AudioSession(null, pid, new File(exe), exe, null, volume, false);
    }

    @Test
    void theAppsLevel() {
        assertEquals(0.81f, CommandVolumeProcess.levelOf(List.of(session(1, "msedge.exe", 0.81f), session(2, "Spotify.exe", 0.3f)), List.of("msedge")));
    }

    @Test
    void unknownWhenItsSessionsDisagree() {
        // Edge playing on Wave Link's Browsers device at 81%, with an idle session on the default device at 100%.
        assertNull(CommandVolumeProcess.levelOf(List.of(session(1, "msedge.exe", 1f), session(2, "msedge.exe", 0.81f)), List.of("msedge")));
    }

    @Test
    void unknownWhenNotRunning() {
        assertNull(CommandVolumeProcess.levelOf(List.of(session(2, "Spotify.exe", 0.3f)), List.of("msedge")));
    }
}
