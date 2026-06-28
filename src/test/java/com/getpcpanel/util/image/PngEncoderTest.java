package com.getpcpanel.util.image;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.awt.image.BufferedImage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Verifies {@link PngEncoder} produces valid PNG bytes by round-tripping through {@link PngDecoder}.
 * Both are pure Java (no ImageIO/AWT Toolkit), so this runs everywhere without a display.
 */
@DisplayName("PngEncoder")
class PngEncoderTest {
    @Test
    @DisplayName("round-trips an ARGB image pixel-for-pixel, including alpha")
    void roundTripPreservesPixels() {
        var width = 7;
        var height = 5;
        var source = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (var y = 0; y < height; y++) {
            for (var x = 0; x < width; x++) {
                // Spread values across all four channels, including fully/partly transparent pixels.
                var a = (x * 37 + y * 11) & 0xFF;
                var r = (x * 23 + 5) & 0xFF;
                var g = (y * 51 + 9) & 0xFF;
                var b = (x * y * 13) & 0xFF;
                source.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);
            }
        }

        var png = PngEncoder.encode(source);
        assertNotNull(png, "encode should produce bytes");

        var decoded = PngDecoder.decode(png);
        assertNotNull(decoded, "PngDecoder should read the encoder's output");
        assertEquals(width, decoded.getWidth());
        assertEquals(height, decoded.getHeight());
        for (var y = 0; y < height; y++) {
            for (var x = 0; x < width; x++) {
                assertEquals(source.getRGB(x, y), decoded.getRGB(x, y), "pixel mismatch at " + x + "," + y);
            }
        }
    }

    @Test
    @DisplayName("returns null for a null image")
    void nullImageReturnsNull() {
        assertNull(PngEncoder.encode(null));
    }
}
