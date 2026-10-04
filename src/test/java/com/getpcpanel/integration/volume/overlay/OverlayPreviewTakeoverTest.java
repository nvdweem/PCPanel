package com.getpcpanel.integration.volume.overlay;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

import com.getpcpanel.profile.Save;
import com.sun.jna.Platform;

/** The settings preview shows the soft-takeover line while soft takeover is on, so its look can be set there. */
class OverlayPreviewTakeoverTest {
    private static boolean hasGreen(boolean softTakeover) throws IOException {
        var save = new Save();
        save.setSoftTakeoverSliders(softTakeover);
        save.setOverlayTakeoverMarkerWidth(4);
        save.setOverlayTakeoverMarkerColor("#00FF00");
        var png = OverlayPreviewRenderer.renderPng(save, 65, "Microsoft Edge");
        assertNotNull(png);
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
        for (var y = 0; y < image.getHeight(); y++) {
            for (var x = 0; x < image.getWidth(); x++) {
                var rgb = image.getRGB(x, y);
                if (((rgb >> 8) & 0xFF) > 200 && ((rgb >> 16) & 0xFF) < 60 && (rgb & 0xFF) < 60) {
                    return true;
                }
            }
        }
        return false;
    }

    @Test
    void theLineShowsWithSoftTakeoverOn() throws IOException {
        assumeTrue(Platform.isWindows(), "the preview renders on Windows only");
        assertTrue(hasGreen(true));
        assertFalse(hasGreen(false));
    }
}
