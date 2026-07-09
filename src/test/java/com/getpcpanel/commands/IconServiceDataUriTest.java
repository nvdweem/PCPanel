package com.getpcpanel.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Unit tests for {@link IconService#decodeImageDataUri(String)} — the path that lets a user's custom
 * overlay image (issue #122) or a snapshotted app icon (issue #121) be rendered from an inline data-URI.
 */
class IconServiceDataUriTest {

    @Test
    void decodesBase64PngDataUri() throws Exception {
        var dataUri = "data:image/png;base64," + Base64.getEncoder().encodeToString(pngBytes(6, 4));

        var decoded = IconService.decodeImageDataUri(dataUri);

        assertNotNull(decoded, "a valid base64 PNG data-URI should decode to an image");
        assertEquals(6, decoded.getWidth());
        assertEquals(4, decoded.getHeight());
    }

    @Test
    void toleratesWhitespaceInPayload() throws Exception {
        var payload = Base64.getEncoder().encodeToString(pngBytes(3, 3));
        var dataUri = "data:image/png;base64,\n" + payload + "\n";

        assertNotNull(IconService.decodeImageDataUri(dataUri));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "data:image/png;base64,not-valid-base64!!!", // undecodable payload
            "data:image/png,rawstuff",                    // not base64-flagged
            "data:image/png;base64",                      // no comma / no payload
            "Spotify",                                    // a bare process name is not a data-URI
    })
    void returnsNullForUnparseableInput(String uri) {
        assertNull(IconService.decodeImageDataUri(uri));
    }

    private static byte[] pngBytes(int w, int h) throws Exception {
        var img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (var x = 0; x < w; x++) {
            for (var y = 0; y < h; y++) {
                img.setRGB(x, y, 0xFF3366CC);
            }
        }
        var out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }
}
