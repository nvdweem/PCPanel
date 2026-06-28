package com.getpcpanel.integration.keyboard;

import com.getpcpanel.integration.keyboard.command.CommandMedia.VolumeButton;

/**
 * Cross-platform keyboard / media-key synthesis. Exactly one implementation is present in a given
 * build, selected by the {@code @WindowsBuild}/{@code @MacBuild}/{@code @LinuxBuild} CDI stereotypes,
 * so callers simply inject {@code Keyboard} (or resolve it via {@code CdiHelper.getBean(Keyboard.class)})
 * and never branch on the operating system. The per-OS backends are package-private implementation
 * details under {@code platform/}: macOS via CoreGraphics {@code CGEvent}s, Windows via
 * {@code User32.SendInput}, Linux via the X11 {@code XTEST} extension — all native (JNA) paths so the
 * GraalVM native image never needs {@link java.awt.Robot} / the AWT windowing toolkit.
 */
public interface Keyboard {
    /** Presses a single "{@code modifier+modifier+key}" combination (e.g. {@code ctrl+shift+A}). */
    void executeKeyStroke(String input);

    /** Types arbitrary text out character-by-character. */
    void typeText(String text);

    /**
     * Sends a multimedia key (play/pause, next, prev, stop, mute). {@code spotify} requests targeting
     * Spotify specifically where the platform supports it (Windows {@code WM_APPCOMMAND} / macOS
     * AppleScript); on Linux the desktop routes the global media key to the active player regardless.
     */
    void sendMediaKey(VolumeButton button, boolean spotify);
}
