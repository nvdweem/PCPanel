package com.getpcpanel.integration.clipboard;

/**
 * Writes plain text to the OS clipboard. One build-time implementation per platform (selected by the
 * {@code @WindowsBuild}/{@code @LinuxBuild}/{@code @MacBuild} stereotypes, exactly like
 * {@link com.getpcpanel.integration.keyboard.Keyboard}), reached from the non-CDI command layer via
 * {@link com.getpcpanel.util.CdiHelper}. Implementations must never throw: clipboard access is a
 * convenience, and a failure is logged and swallowed so a button press can't crash the app.
 */
public interface ClipboardWriter {
    /**
     * Sets the clipboard to {@code text}. A {@code null} or empty string is ignored. Best-effort: any
     * platform failure is logged and swallowed, never propagated.
     */
    void setText(String text);
}
