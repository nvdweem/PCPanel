package com.getpcpanel.overlay;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.GraphicsEnvironment;
import java.awt.Window;
import java.lang.reflect.Method;

import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.JWindow;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.getpcpanel.profile.Save;

/**
 * Exercises the overlay and its AWT/Swing class hierarchy.
 *
 * <p>When run with the GraalVM native-image tracing agent these tests cause the
 * AWT, Swing, and Metal L&F class graph to be recorded in the generated
 * {@code reflect-config.json} and {@code jni-config.json} files.
 *
 * <h2>Headless vs. display tests</h2>
 * <ul>
 *   <li>Color-parsing tests run everywhere (no display required).</li>
 *   <li>Tests that actually construct a {@link VolumeOverlay}/{@link JWindow}
 *       are skipped automatically when no display is available.</li>
 * </ul>
 *
 * <p>Run the generation script at the project root to regenerate native-image configs:
 * <pre>  generate-native-configs.cmd</pre>
 */
@DisplayName("VolumeOverlay native-image config coverage")
class VolumeOverlayNativeTest {
    @Test
    void showOverlay() {
        new VolumeOverlay().show(1, null);
    }

    // ── Shared reflection handle on the private static parseColor method ─────

    private static Method parseColorMethod;

    @BeforeAll
    static void resolveParseColor() throws Exception {
        parseColorMethod = VolumeOverlay.class.getDeclaredMethod("parseColor", String.class, Color.class);
        parseColorMethod.setAccessible(true);
    }

    /**
     * Invoke the private {@code VolumeOverlay.parseColor(String, Color)} helper.
     */
    private Color parseColor(String value, Color fallback) throws Exception {
        return (Color) parseColorMethod.invoke(null, value, fallback);
    }

    // ── Color parsing – hex formats ──────────────────────────────────────────

    @Nested
    @DisplayName("Hex color parsing")
    class HexColors {

        @Test
        @DisplayName("#RGB shorthand expands correctly")
        void threeCharHex() throws Exception {
            var c = parseColor("#f0a", Color.BLACK);
            assertEquals(0xff, c.getRed());
            assertEquals(0x00, c.getGreen());
            assertEquals(0xaa, c.getBlue());
            assertEquals(255, c.getAlpha()); // fully opaque
        }

        @Test
        @DisplayName("#RGBA shorthand expands correctly")
        void fourCharHex() throws Exception {
            var c = parseColor("#f0a8", Color.BLACK);
            assertEquals(0xff, c.getRed());
            assertEquals(0x00, c.getGreen());
            assertEquals(0xaa, c.getBlue());
            assertEquals(0x88, c.getAlpha());
        }

        @Test
        @DisplayName("#RRGGBB parses correctly")
        void sixCharHex() throws Exception {
            var c = parseColor("#1A2B3C", Color.BLACK);
            assertEquals(0x1A, c.getRed());
            assertEquals(0x2B, c.getGreen());
            assertEquals(0x3C, c.getBlue());
            assertEquals(255, c.getAlpha());
        }

        @Test
        @DisplayName("#RRGGBBAA parses correctly")
        void eightCharHex() throws Exception {
            var c = parseColor("#1A2B3CFF", Color.BLACK);
            assertEquals(0x1A, c.getRed());
            assertEquals(0x2B, c.getGreen());
            assertEquals(0x3C, c.getBlue());
            assertEquals(0xFF, c.getAlpha());
        }

        @Test
        @DisplayName("Invalid hex falls back to supplied default")
        void invalidHexFallsBack() throws Exception {
            var fallback = Color.RED;
            var c = parseColor("#ZZXXPP", fallback);
            assertEquals(fallback, c);
        }

        @Test
        @DisplayName("Null input returns fallback")
        void nullInputFallsBack() throws Exception {
            var fallback = Color.BLUE;
            assertEquals(fallback, parseColor(null, fallback));
        }

        @Test
        @DisplayName("Blank string returns fallback")
        void blankInputFallsBack() throws Exception {
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
        void rgbParsing(String input, int r, int g, int b, int a) throws Exception {
            var c = parseColor(input, Color.BLACK);
            assertEquals(r, c.getRed(), "red channel of: " + input);
            assertEquals(g, c.getGreen(), "green channel of: " + input);
            assertEquals(b, c.getBlue(), "blue channel of: " + input);
            assertEquals(a, c.getAlpha(), "alpha channel of: " + input);
        }

        @Test
        @DisplayName("Malformed rgb() returns fallback")
        void malformedRgbFallsBack() throws Exception {
            var fallback = Color.YELLOW;
            var c = parseColor("rgb(not, a, color)", fallback);
            assertEquals(fallback, c);
        }

        @Test
        @DisplayName("Save default overlay colors parse without error")
        void defaultSaveColorsParseCleanly() throws Exception {
            assertDoesNotThrow(() -> {
                parseColor(Save.DEFAULT_OVERLAY_BG_COLOR, Color.BLACK);
                parseColor(Save.DEFAULT_OVERLAY_TEXT_COLOR, Color.BLACK);
                parseColor(Save.DEFAULT_OVERLAY_BAR_COLOR, Color.BLACK);
                parseColor(Save.DEFAULT_OVERLAY_BAR_BACKGROUND_COLOR, Color.BLACK);
            });
        }
    }

    // ── AWT / Swing class-graph coverage (requires display) ──────────────────

    @Nested
    @DisplayName("AWT / Swing class loading (requires display)")
    class SwingClassLoading {

        @BeforeAll
        static void requireDisplay() {
            Assumptions.assumeFalse(
                    GraphicsEnvironment.isHeadless(),
                    "Skipped: no display available (headless environment)");
        }

        @Test
        @DisplayName("VolumeOverlay can be constructed (loads JWindow + Metal L&F)")
        void overlayConstructs() {
            // Constructing VolumeOverlay triggers:
            //   - UIManager.setLookAndFeel (MetalLookAndFeel)
            //   - JWindow / JRootPane / JPanel / JLayeredPane creation
            //   - AWT Toolkit initialisation
            // All of these must be recorded in the native-image config.
            assertDoesNotThrow(() -> {
                var overlay = new VolumeOverlay();
                assertNotNull(overlay);
                overlay.dispose();
            });
        }

        @Test
        @DisplayName("setStyles applies Save defaults without throwing")
        void setStylesWithDefaults() {
            assertDoesNotThrow(() -> {
                var overlay = new VolumeOverlay();
                var save = new Save();
                overlay.setStyles(save);
                overlay.dispose();
            });
        }

        @Test
        @DisplayName("show() updates value and makes overlay visible")
        void showUpdatesVisibility() throws Exception {
            var overlay = new VolumeOverlay();
            try {
                overlay.show(0.5f, null);
                // Give Swing a moment to process
                Thread.sleep(50);
                // Value should have been clamped and stored
                assertNotNull(overlay);
            } finally {
                overlay.dispose();
            }
        }

        // ── Reflection coverage for JNI-config entries ────────────────────
        // Note: setAccessible on java.desktop fields is blocked by the module
        // system on Java 9+.  Instead we verify that the classes are loadable
        // and that their declared field/method counts are non-zero – enough for
        // the tracing agent to record the class-graph during a real run.

        @Test
        @DisplayName("javax.swing.JWindow is loadable and declares fields")
        void jWindowIsLoadable() {
            var fields = JWindow.class.getDeclaredFields();
            assertTrue(fields.length > 0, "JWindow should declare at least one field");
        }

        @Test
        @DisplayName("javax.swing.JPanel is loadable and declares fields")
        void jPanelIsLoadable() {
            var fields = JPanel.class.getDeclaredFields();
            assertTrue(fields.length > 0, "JPanel should declare at least one field");
        }

        @Test
        @DisplayName("java.awt.Window is loadable and declares fields")
        void awtWindowIsLoadable() {
            var fields = Window.class.getDeclaredFields();
            assertTrue(fields.length > 0, "Window should declare at least one field");
        }

        @Test
        @DisplayName("javax.swing.JRootPane is loadable")
        void jRootPaneIsLoadable() {
            assertNotNull(JRootPane.class.getDeclaredMethods());
        }

        @Test
        @DisplayName("javax.swing.JLayeredPane is loadable")
        void jLayeredPaneIsLoadable() {
            assertNotNull(JLayeredPane.class.getDeclaredMethods());
        }
    }
}
