package com.getpcpanel.util.image;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The overlay's bundled action icons are decoded by {@link PngDecoder} (ImageIO is unusable in the native image), so
 * each must be a PNG it reads.
 */
class BundledOverlayIconsTest {
    @ParameterizedTest
    @ValueSource(strings = { "/assets/brightness.png", "/assets/device.png", "/assets/obs.png", "/assets/voicemeeter.png" })
    void decodes(String path) throws IOException {
        try (var in = getClass().getResourceAsStream(path)) {
            assertNotNull(in, path);
            var image = PngDecoder.decode(in.readAllBytes());
            assertNotNull(image, path);
            assertEquals(64, image.getWidth());
            assertEquals(64, image.getHeight());
        }
    }
}
