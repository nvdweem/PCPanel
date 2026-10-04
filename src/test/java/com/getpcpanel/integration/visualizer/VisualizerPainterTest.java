package com.getpcpanel.integration.visualizer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import com.getpcpanel.integration.visualizer.VisualizerPainter.Layout;
import com.getpcpanel.integration.visualizer.VisualizerPainter.Music;
import com.getpcpanel.profile.dto.VisualizerConfig;
import com.getpcpanel.profile.dto.VisualizerConfig.VisualizerStyle;

class VisualizerPainterTest {
    private static final Layout PRO = new Layout(5, 4, true, true);
    private static final Layout MINI = new Layout(4, 0, false, false);
    private static final float[] NO_POSITIONS = nan(9);

    private static VisualizerConfig config(VisualizerStyle style) {
        var cfg = new VisualizerConfig();
        cfg.setWhen(VisualizerConfig.VisualizerWhen.ALWAYS);
        cfg.setStyle(style);
        cfg.setLowColor("#0000FF");
        cfg.setHighColor("#FF0000");
        return cfg;
    }

    private static float[] nan(int n) {
        var out = new float[n];
        Arrays.fill(out, Float.NaN);
        return out;
    }

    @Test
    void fiveKnobsPutTheWholeMixInTheMiddle() {
        assertEquals(0, VisualizerPainter.knobBand(0, 5));
        assertEquals(1, VisualizerPainter.knobBand(1, 5));
        assertEquals(VisualizerPainter.BAND_OVERALL, VisualizerPainter.knobBand(2, 5));
        assertEquals(2, VisualizerPainter.knobBand(3, 5));
        assertEquals(3, VisualizerPainter.knobBand(4, 5));
        for (var k = 0; k < 4; k++) {
            assertEquals(k, VisualizerPainter.knobBand(k, 4));
        }
    }

    @Test
    void twoColorsBlendWithTheBandAndDimWhenQuiet() {
        var music = new Music(new float[] { 1, 0, 0.5f, 0 }, 0.5f, 0);
        var paint = VisualizerPainter.paint(config(VisualizerStyle.TWO_COLORS), PRO, music, NO_POSITIONS, 0, false);
        assertEquals("#FF0000", paint.knobs()[0], "a full band is the loud colour at full brightness");
        assertEquals("#00000F", paint.knobs()[1], "a quiet band is the quiet colour, nearly dark");
        assertEquals("#800080", VisualizerPainter.hex(VisualizerPainter.mix(0x0000FF, 0xFF0000, 0.5f)));
    }

    @Test
    void slidersFillFromTheBottom() {
        var music = new Music(new float[] { 0, 0.5f, 1, 0.25f }, 0.5f, 0);
        var paint = VisualizerPainter.paint(config(VisualizerStyle.TWO_COLORS), PRO, music, NO_POSITIONS, 0, false);
        assertArrayEquals(new String[] { "#000000", "#0000FF", "#0000FF", "#000080" }, paint.sliderBottoms());
        assertArrayEquals(new String[] { "#000000", "#000000", "#FF0000", "#000000" }, paint.sliderTops());
    }

    @Test
    void pulseMovesEveryLightWithTheWholeMix() {
        var music = new Music(new float[] { 1, 0, 1, 0 }, 1, 0);
        var paint = VisualizerPainter.paint(config(VisualizerStyle.PULSE), PRO, music, NO_POSITIONS, 0, false);
        for (var knob : paint.knobs()) {
            assertEquals("#FF0000", knob);
        }
        assertEquals("#FF0000", paint.logo());
        for (var i = 0; i < 4; i++) {
            assertEquals("#FF0000", paint.sliderTops()[i], "slider " + i);
        }
    }

    @Test
    void rainbowRunsFromRedForTheBass() {
        var music = new Music(new float[] { 1, 1, 1, 1 }, 1, 0);
        var paint = VisualizerPainter.paint(config(VisualizerStyle.RAINBOW), PRO, music, NO_POSITIONS, 0, false);
        assertEquals("#FF0000", paint.knobs()[0]);
        assertNotEquals(paint.knobs()[0], paint.knobs()[4], "treble has another hue");
        var later = VisualizerPainter.paint(config(VisualizerStyle.RAINBOW), PRO, music, NO_POSITIONS, 10, false);
        assertNotEquals(paint.knobs()[0], later.knobs()[0], "the hues turn over time");
    }

    @Test
    void aBeatFlashesTowardsWhite() {
        var calm = VisualizerPainter.paint(config(VisualizerStyle.TWO_COLORS), PRO, new Music(new float[] { 1, 0, 0, 0 }, 0.5f, 0), NO_POSITIONS, 0, false);
        var beat = VisualizerPainter.paint(config(VisualizerStyle.TWO_COLORS), PRO, new Music(new float[] { 1, 0, 0, 0 }, 0.5f, 1), NO_POSITIONS, 0, false);
        assertEquals("#FF0000", calm.knobs()[0]);
        assertEquals("#FF9999", beat.knobs()[0]);
        assertNotEquals(calm.logo(), beat.logo());
    }

    @Test
    void drivesOnlyTheChosenLights() {
        var cfg = config(VisualizerStyle.TWO_COLORS);
        cfg.setLights(new String[] { "knob:1", "slider:2", "label:3" });
        var paint = VisualizerPainter.paint(cfg, PRO, Music.QUIET, NO_POSITIONS, 0, false);
        assertNull(paint.knobs()[0]);
        assertNotNull(paint.knobs()[1]);
        assertNull(paint.sliderBottoms()[0]);
        assertNotNull(paint.sliderBottoms()[2]);
        assertNotNull(paint.sliderTops()[2]);
        assertNull(paint.labels()[2]);
        assertNotNull(paint.labels()[3]);
        assertNull(paint.logo());

        var all = VisualizerPainter.paint(cfg, PRO, Music.QUIET, NO_POSITIONS, 0, true);
        assertNotNull(all.knobs()[0], "an animated profile hands every light over");
        assertNotNull(all.logo());
    }

    @Test
    void aMovedControlShowsItsPosition() {
        var positions = nan(9);
        positions[0] = 0.5f;
        positions[5] = 1f; // slider 0
        var paint = VisualizerPainter.paint(config(VisualizerStyle.TWO_COLORS), PRO, Music.QUIET, positions, 0, false);
        assertEquals(VisualizerPainter.hex(VisualizerPainter.scale(0x800080, 0.53f)), paint.knobs()[0]);
        assertEquals("#0000FF", paint.sliderBottoms()[0]);
        assertEquals("#FF0000", paint.sliderTops()[0]);
        assertEquals("#000000", paint.sliderBottoms()[1], "the others keep following the music");
    }

    @Test
    void aMiniHasFourKnobsAndNothingElse() {
        var paint = VisualizerPainter.paint(config(VisualizerStyle.RAINBOW), MINI, Music.QUIET, nan(4), 0, false);
        assertEquals(4, paint.knobs().length);
        assertEquals(0, paint.sliderBottoms().length);
        assertEquals(0, paint.labels().length);
        assertNull(paint.logo());
    }

    @Test
    void framesCompareByTheirColours() {
        var a = VisualizerPainter.paint(config(VisualizerStyle.TWO_COLORS), PRO, Music.QUIET, NO_POSITIONS, 0, false);
        var b = VisualizerPainter.paint(config(VisualizerStyle.TWO_COLORS), PRO, Music.QUIET, NO_POSITIONS, 0, false);
        assertEquals(a, b);
    }
}
