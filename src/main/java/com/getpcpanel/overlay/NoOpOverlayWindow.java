package com.getpcpanel.overlay;

import java.awt.Dimension;
import java.awt.Image;

import com.getpcpanel.profile.Save;

/**
 * Overlay implementation that does nothing, used on macOS and Linux. The on-screen volume overlay is
 * drawn with a Win32 layered window (Windows) via JNA; macOS and Linux native images have no AWT-free
 * overlay yet, so the overlay is simply disabled there rather than pulling in the AWT windowing toolkit.
 *
 * <p>Only constructs {@link Dimension} (a pure-Java value class), never touching the AWT toolkit, so it
 * is safe in the libawt-less native images.
 */
public class NoOpOverlayWindow implements OverlayWindow {
    @Override
    public void show(float value, Image icon) {
        // no overlay on macOS
    }

    @Override
    public void setStyles(Save save) {
        // no styling without an overlay
    }

    @Override
    public void setLocation(int x, int y) {
        // nothing to position
    }

    @Override
    public int getWidth() {
        return 0;
    }

    @Override
    public int getHeight() {
        return 0;
    }

    @Override
    public Dimension getScreenSize() {
        return new Dimension(0, 0);
    }
}
