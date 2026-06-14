package com.getpcpanel.overlay;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.Color;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.getpcpanel.profile.Save;

/**
 * Exercises {@link OverlayRenderer#parseColor(String, Color)}, the headless colour parser shared by
 * the overlay renderer. This uses only headless Java2D ({@link Color}), never the AWT windowing
 * toolkit, so it is safe to run everywhere without a display.
 */
@DisplayName("OverlayRenderer colour parsing")
class OverlayRendererColorTest {
    private Color parseColor(String value, Color fallback) {
        return OverlayRenderer.parseColor(value, fallback);
    }

    // ── Color parsing – hex formats ──────────────────────────────────────────

    @Nested
    @DisplayName("Hex color parsing")
    class HexColors {

        @Test
        @DisplayName("#RGB shorthand expands correctly")
        void threeCharHex() {
            var c = parseColor("#f0a", Color.BLACK);
            assertEquals(0xff, c.getRed());
            assertEquals(0x00, c.getGreen());
            assertEquals(0xaa, c.getBlue());
            assertEquals(255, c.getAlpha()); // fully opaque
        }

        @Test
        @DisplayName("#RGBA shorthand expands correctly")
        void fourCharHex() {
            var c = parseColor("#f0a8", Color.BLACK);
            assertEquals(0xff, c.getRed());
            assertEquals(0x00, c.getGreen());
            assertEquals(0xaa, c.getBlue());
            assertEquals(0x88, c.getAlpha());
        }

        @Test
        @DisplayName("#RRGGBB parses correctly")
        void sixCharHex() {
            var c = parseColor("#1A2B3C", Color.BLACK);
            assertEquals(0x1A, c.getRed());
            assertEquals(0x2B, c.getGreen());
            assertEquals(0x3C, c.getBlue());
            assertEquals(255, c.getAlpha());
        }

        @Test
        @DisplayName("#RRGGBBAA parses correctly")
        void eightCharHex() {
            var c = parseColor("#1A2B3CFF", Color.BLACK);
            assertEquals(0x1A, c.getRed());
            assertEquals(0x2B, c.getGreen());
            assertEquals(0x3C, c.getBlue());
            assertEquals(0xFF, c.getAlpha());
        }

        @Test
        @DisplayName("Invalid hex falls back to supplied default")
        void invalidHexFallsBack() {
            var fallback = Color.RED;
            var c = parseColor("#ZZXXPP", fallback);
            assertEquals(fallback, c);
        }

        @Test
        @DisplayName("Null input returns fallback")
        void nullInputFallsBack() {
            var fallback = Color.BLUE;
            assertEquals(fallback, parseColor(null, fallback));
        }

        @Test
        @DisplayName("Blank string returns fallback")
        void blankInputFallsBack() {
            var fallback = Color.GREEN;
            assertEquals(fallback, parseColor("   ", fallback));
        }
    }

    // ── Color parsing – rgb()/rgba() ─────────────────────────────────────────

    @Nested
    @DisplayName("rgb()/rgba() color parsing")
    class RgbColors {

        @CsvSource(delimiterString = "|", value = {
                "rgb(0, 148, 197)      | 0   | 148 | 197 | 255",
                "rgb(255, 255, 255)    | 255 | 255 | 255 | 255",
                "rgb(0, 0, 0)          | 0   | 0   | 0   | 255",
                "rgba(80, 80, 90, 210) | 80  | 80  | 90  | 210",
                "rgba(0, 0, 0, 0)      | 0   | 0   | 0   | 0",
                "rgba(255,255,255,1)   | 255 | 255 | 255 | 255",  // alpha=1 → 255
                "rgba(0,200,230,0.5)   | 0   | 200 | 230 | 128",  // alpha=0.5 → ~128
        })
        @ParameterizedTest(name = "{0}")
        @DisplayName("parses correctly")
        void rgbParsing(String input, int r, int g, int b, int a) {
            var c = parseColor(input, Color.BLACK);
            assertEquals(r, c.getRed(), "red channel of: " + input);
            assertEquals(g, c.getGreen(), "green channel of: " + input);
            assertEquals(b, c.getBlue(), "blue channel of: " + input);
            assertEquals(a, c.getAlpha(), "alpha channel of: " + input);
        }

        @Test
        @DisplayName("Malformed rgb() returns fallback")
        void malformedRgbFallsBack() {
            var fallback = Color.YELLOW;
            var c = parseColor("rgb(not, a, color)", fallback);
            assertEquals(fallback, c);
        }

        @Test
        @DisplayName("Save default overlay colors parse without error")
        void defaultSaveColorsParseCleanly() {
            assertDoesNotThrow(() -> {
                parseColor(Save.DEFAULT_OVERLAY_BG_COLOR, Color.BLACK);
                parseColor(Save.DEFAULT_OVERLAY_TEXT_COLOR, Color.BLACK);
                parseColor(Save.DEFAULT_OVERLAY_BAR_COLOR, Color.BLACK);
                parseColor(Save.DEFAULT_OVERLAY_BAR_BACKGROUND_COLOR, Color.BLACK);
            });
        }
    }
}
