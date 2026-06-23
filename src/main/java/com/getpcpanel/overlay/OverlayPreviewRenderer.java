package com.getpcpanel.overlay;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import javax.annotation.Nullable;

import javax.imageio.ImageIO;

import com.getpcpanel.profile.Save;
import com.sun.jna.Platform;

import lombok.extern.log4j.Log4j2;

/**
 * Renders the <em>real</em> overlay (the same {@link OverlayRenderer} the on-screen overlay uses) to a
 * PNG, so the settings page can show a pixel-identical preview instead of a hand-maintained CSS mock
 * that can drift from the actual rendering.
 *
 * <p>Windows only: the headless Java2D pipeline (BufferedImage) needs libawt, which only the Windows
 * native image bundles. Returns {@code null} elsewhere (the UI falls back to its note/mock).
 */
@Log4j2
public final class OverlayPreviewRenderer {
    private OverlayPreviewRenderer() {
    }

    @Nullable
    public static byte[] renderPng(Save save, int valuePercent, String name) {
        if (!Platform.isWindows()) {
            return null;
        }
        try {
            var renderer = new OverlayRenderer();
            var h = renderer.setStyles(save);
            var w = renderer.width();
            renderer.setValue(valuePercent);
            renderer.setName(name);
            renderer.setIcon(sampleIcon(save.getOverlayIconSize()));

            var img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB_PRE);
            var g = img.createGraphics();
            try {
                renderer.render(g, w, h);
            } finally {
                g.dispose();
            }
            var out = new ByteArrayOutputStream();
            ImageIO.write(img, "png", out);
            return out.toByteArray();
        } catch (Throwable t) {
            log.warn("Overlay preview render failed", t);
            return null;
        }
    }

    /** A clearly-visible rounded-square stand-in for the app icon, sized like the real one. */
    private static BufferedImage sampleIcon(int size) {
        if (size <= 0) {
            return null;
        }
        var img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        var g = img.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
        var arc = Math.max(4, size / 4);
        g.setColor(new Color(120, 170, 235));
        g.fillRoundRect(0, 0, size, size, arc, arc);
        // a small inner glyph so it reads as an icon
        g.setColor(new Color(255, 255, 255, 220));
        var inset = Math.max(2, size / 4);
        g.fillOval(inset, inset, size - 2 * inset, size - 2 * inset);
        g.dispose();
        return img;
    }
}
