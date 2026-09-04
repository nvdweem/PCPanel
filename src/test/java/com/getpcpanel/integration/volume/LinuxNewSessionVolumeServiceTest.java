package com.getpcpanel.integration.volume;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.getpcpanel.integration.volume.command.CommandVolumeProcess;
import com.getpcpanel.integration.volume.platform.AudioSession;
import com.getpcpanel.integration.volume.platform.AudioSessionEvent;
import com.getpcpanel.integration.volume.platform.EventType;

import one.util.streamex.StreamEx;

class LinuxNewSessionVolumeServiceTest {
    private final LinuxNewSessionVolumeService sut = new LinuxNewSessionVolumeService();

    private static AudioSessionEvent event(String executable, String title) {
        return new AudioSessionEvent(new AudioSession(null, 1234, new File(executable), title, null, 1f, false), EventType.ADDED);
    }

    private static CommandVolumeProcess binding(String device, String... processNames) {
        return new CommandVolumeProcess(List.of(processNames), device, false, null);
    }

    @Test
    void matchesExecutableOrTitle() {
        var zen = event("zen-bin", "Zen");

        assertTrue(sut.isProcessAndDevice(zen, binding("", "chromium", "zen", "chrome")));
        assertTrue(sut.isProcessAndDevice(zen, binding("", "zen-bin")));
        assertTrue(sut.isProcessAndDevice(zen, binding("", "ZEN")));
        assertFalse(sut.isProcessAndDevice(zen, binding("", "chromium", "chrome")));
    }

    @Test
    void ignoresTrailingExe() {
        var game = event("deadlock.exe", "deadlock.exe");

        assertTrue(sut.isProcessAndDevice(game, binding("", "Deadlock")));
        assertTrue(sut.isProcessAndDevice(game, binding("", "deadlock.exe")));
    }

    @Test
    void honoursDeviceScope() {
        var mpv = event("mpv", "mpv");

        assertTrue(sut.isProcessAndDevice(mpv, binding("", "mpv")));
        assertTrue(sut.isProcessAndDevice(mpv, binding("*", "mpv")));
        assertFalse(sut.isProcessAndDevice(mpv, binding("alsa_output.pci-0000_0c_00.4", "mpv")));
    }

    @Test
    void blankNamesMatchNothing() {
        var sparse = event("/", "Spotify");

        assertFalse(sut.isProcessAndDevice(sparse, binding("")));
        assertFalse(sut.isProcessAndDevice(sparse, binding("", ".exe")));
        assertTrue(sut.isProcessAndDevice(sparse, binding("", "Spotify")));
    }

    /**
     * A binding is matched with the session's own match keys, so a platform identifier that is neither the
     * executable nor the title (on PulseAudio: the portal app id) still restores the volume.
     */
    @Test
    void honoursPlatformSpecificMatchKeys() {
        var spotify = new AudioSession(null, 1234, new File("/"), "Spotify", null, 1f, false) {
            @Override
            protected Collection<String> matchKeys() {
                return StreamEx.of(super.matchKeys()).append("com.spotify.Client").toList();
            }
        };
        var event = new AudioSessionEvent(spotify, EventType.ADDED);

        assertTrue(sut.isProcessAndDevice(event, binding("", "Spotify")), "the title still matches");
        assertTrue(sut.isProcessAndDevice(event, binding("", "com.spotify.Client")), "the portal app id must match too");
        assertFalse(sut.isProcessAndDevice(event, binding("", "com.other.Client")));
    }

    /**
     * The focus dial stores a volume under the focused window's identifier, which is rarely the stream's
     * executable name - the stored volume must still be found when the app's stream appears.
     */
    @Test
    void storedFocusVolumeMatchesOnAnyIdentifier() {
        // The focused window reports "Zen"; the stream it produces is the binary "zen-bin".
        sut.handleFocusVolumeRequest("Zen", 0.42f);
        assertEquals(Optional.of("zen"), sut.storedFocusTarget(event("zen-bin", "Zen").session()));

        // A Proton window is named after the game, its stream after the .exe.
        sut.handleFocusVolumeRequest("Deadlock", 0.42f);
        assertEquals(Optional.of("deadlock"), sut.storedFocusTarget(event("deadlock.exe", "deadlock.exe").session()));

        // An unrelated stream must not pick up either stored volume.
        assertEquals(Optional.empty(), sut.storedFocusTarget(event("mpv", "mpv").session()));
    }
}
