package com.getpcpanel.integration.volume;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.getpcpanel.integration.volume.command.CommandVolumeProcess;
import com.getpcpanel.integration.volume.platform.AudioSession;
import com.getpcpanel.integration.volume.platform.AudioSessionEvent;
import com.getpcpanel.integration.volume.platform.EventType;

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
}
