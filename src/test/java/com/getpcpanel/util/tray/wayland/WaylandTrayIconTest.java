package com.getpcpanel.util.tray.wayland;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Comparator;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

class WaylandTrayIconTest {
    /**
     * The tray pixmap is generated from app-icon.png by packaging/make-icon-assets.py, and an icon change that skips
     * the script leaves the Linux tray showing the old artwork (#181). Compares the largest frame with app-icon.png
     * box-filtered down to the same size: resampling differences stay well below the threshold, other artwork does not.
     *
     * <p>Only {@code ImageIO} decoding and pixel reads: CI runs the tests under the native-image tracing agent with AWT
     * not headless, and anything that starts the AWT toolkit (such as {@code Image.getScaledInstance}) gets recorded
     * into the native-image metadata - on macOS that pulls {@code com.apple.eawt} into the image and breaks the build.
     */
    @Test
    void pixmapMatchesTheAppIcon() throws IOException {
        var frames = WaylandTrayIcon.pixmap();
        assertFalse(frames.isEmpty(), "tray-icon.argb is missing or unreadable");
        var largest = frames.stream().max(Comparator.comparingInt(IconPixmap::getWidth)).orElseThrow();
        var size = largest.getWidth();

        var source = ImageIO.read(Path.of("app-icon.png").toFile());
        var factor = source.getWidth() / size;
        assertEquals(source.getWidth(), factor * size, "app-icon.png is not a whole multiple of the largest frame");
        assertEquals(source.getWidth(), source.getHeight(), "app-icon.png is not square");

        var data = largest.getData();
        long difference = 0;
        for (var y = 0; y < size; y++) {
            for (var x = 0; x < size; x++) {
                var expected = average(source, x * factor, y * factor, factor);
                var i = (y * size + x) * 4;
                for (var channel = 0; channel < 4; channel++) {
                    difference += Math.abs(expected[channel] - (data[i + channel] & 0xFF));
                }
            }
        }
        var mean = (double) difference / (size * size * 4);
        assertTrue(mean < 8, () -> "tray-icon.argb differs from app-icon.png (mean channel difference " + mean
                + "); run packaging/make-icon-assets.py");
    }

    /** The A, R, G and B averages of a {@code factor} x {@code factor} block, colour weighted by alpha. */
    private static int[] average(BufferedImage image, int left, int top, int factor) {
        long a = 0, r = 0, g = 0, b = 0;
        for (var y = top; y < top + factor; y++) {
            for (var x = left; x < left + factor; x++) {
                var argb = image.getRGB(x, y);
                var alpha = argb >>> 24;
                a += alpha;
                r += (argb >> 16 & 0xFF) * alpha;
                g += (argb >> 8 & 0xFF) * alpha;
                b += (argb & 0xFF) * alpha;
            }
        }
        if (a == 0) {
            return new int[4];
        }
        return new int[] { (int) (a / (factor * factor)), (int) (r / a), (int) (g / a), (int) (b / a) };
    }
}
