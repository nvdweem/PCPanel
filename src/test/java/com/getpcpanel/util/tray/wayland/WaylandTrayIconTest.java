package com.getpcpanel.util.tray.wayland;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.RenderingHints;
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
     * scaled to the same size: resampling differences stay well below the threshold, other artwork does not.
     */
    @Test
    void pixmapMatchesTheAppIcon() throws IOException {
        var frames = WaylandTrayIcon.pixmap();
        assertFalse(frames.isEmpty(), "tray-icon.argb is missing or unreadable");
        var largest = frames.stream().max(Comparator.comparingInt(IconPixmap::getWidth)).orElseThrow();

        var size = largest.getWidth();
        var expected = scaled(ImageIO.read(Path.of("app-icon.png").toFile()), size);
        var data = largest.getData();
        long difference = 0;
        for (var y = 0; y < size; y++) {
            for (var x = 0; x < size; x++) {
                var argb = expected.getRGB(x, y);
                var i = (y * size + x) * 4;
                for (var channel = 0; channel < 4; channel++) {
                    var want = argb >>> (24 - channel * 8) & 0xFF;
                    difference += Math.abs(want - (data[i + channel] & 0xFF));
                }
            }
        }
        var mean = (double) difference / (size * size * 4);
        assertTrue(mean < 8, () -> "tray-icon.argb differs from app-icon.png (mean channel difference " + mean
                + "); run packaging/make-icon-assets.py");
    }

    private static BufferedImage scaled(BufferedImage source, int size) {
        var image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(source.getScaledInstance(size, size, java.awt.Image.SCALE_AREA_AVERAGING), 0, 0, null);
        g.dispose();
        return image;
    }
}
