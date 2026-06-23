package com.getpcpanel.overlay;

import com.getpcpanel.profile.Save;

/**
 * Abstraction over the on-screen volume overlay window.
 *
 * <p>Two implementations exist:
 * <ul>
 *   <li>{@link Win32VolumeOverlay} – a JNA-backed Win32 layered window, used on Windows. This
 *       avoids the AWT windowing toolkit ({@code sun.awt.windows.WToolkit}), which is unsupported
 *       in the Quarkus/GraalVM native image and crashes its native event loop.</li>
 *   <li>{@link NoOpOverlayWindow} – a disabled overlay, used on macOS and Linux, which have no
 *       AWT-free overlay implementation yet.</li>
 * </ul>
 *
 * Implementations are responsible for marshalling work onto their own UI thread.
 */
public interface OverlayWindow {
    /** Render the overlay for the given content and make it visible (auto-hides). */
    void show(OverlayContent content);

    /** Apply persisted styling (colors, sizes, number visibility). */
    void setStyles(Save save);

    /** Position the overlay's top-left corner at the given screen coordinates. */
    void setLocation(int x, int y);

    int getWidth();

    int getHeight();

    /** The size of the primary screen, used to position the overlay. */
    ScreenSize getScreenSize();
}
