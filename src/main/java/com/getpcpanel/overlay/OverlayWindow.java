package com.getpcpanel.overlay;

import java.awt.Dimension;
import java.awt.Image;

import com.getpcpanel.profile.Save;

/**
 * Abstraction over the on-screen volume overlay window.
 *
 * <p>Two implementations exist:
 * <ul>
 *   <li>{@link VolumeOverlay} – a Swing {@code JWindow}, used on Linux/macOS and the JVM.</li>
 *   <li>{@link Win32VolumeOverlay} – a JNA-backed Win32 layered window, used on Windows. This
 *       avoids the AWT windowing toolkit ({@code sun.awt.windows.WToolkit}), which is unsupported
 *       in the Quarkus/GraalVM native image and crashes its native event loop on Windows.</li>
 * </ul>
 *
 * Implementations are responsible for marshalling work onto their own UI thread.
 */
public interface OverlayWindow {
    /** Render the overlay for the given value (0..1) and icon and make it visible (auto-hides). */
    void show(float value, Image icon);

    /** Apply persisted styling (colors, sizes, number visibility). */
    void setStyles(Save save);

    /** Position the overlay's top-left corner at the given screen coordinates. */
    void setLocation(int x, int y);

    int getWidth();

    int getHeight();

    /** The size of the primary screen, used to position the overlay. */
    Dimension getScreenSize();
}
