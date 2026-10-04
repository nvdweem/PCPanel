package com.getpcpanel.integration.visualizer;

import java.util.Arrays;

import javax.annotation.Nullable;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;

import com.getpcpanel.profile.dto.VisualizerConfig;
import com.getpcpanel.profile.dto.VisualizerConfig.VisualizerStyle;

/**
 * Turns one frame of the music into a colour per light. Pure: everything it needs comes in as arguments.
 *
 * <p>Five knobs show bass, low mids, the whole mix, high mids and treble; four knobs the four bands. Sliders fill from
 * the bottom with the four bands (a slider strip takes two colours, so the bottom half lights first and the top half
 * past 50%); their labels brighten with the same band. The logo follows the whole mix. A beat flashes the knobs and
 * logo towards white. A control that was just moved shows its position instead of the music.
 */
final class VisualizerPainter {
    static final int BAND_OVERALL = -1;
    private static final int WHITE = 0xFFFFFF;

    /** What a device has: knob, slider (with label) and logo lights. */
    record Layout(int knobs, int sliders, boolean labels, boolean logo) {
    }

    /**
     * One frame's music.
     *
     * @param bands   0..1 per band: bass, low mids, high mids, treble
     * @param overall 0..1 for the whole mix
     * @param glow    0..1 beat flash, fading after each beat
     */
    record Music(float[] bands, float overall, float glow) {
        static final Music QUIET = new Music(new float[BandAnalyzer.BANDS], 0, 0);
    }

    /** The colours of one frame, {@code #RRGGBB}; null where the visualizer doesn't drive that light. */
    record Paint(String[] knobs, String[] sliderBottoms, String[] sliderTops, String[] labels, @Nullable String logo) {
        @Override
        public boolean equals(Object o) {
            return o instanceof Paint p && Arrays.equals(knobs, p.knobs) && Arrays.equals(sliderBottoms, p.sliderBottoms) && Arrays.equals(sliderTops, p.sliderTops)
                    && Arrays.equals(labels, p.labels) && StringUtils.equals(logo, p.logo);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(knobs) * 31 + Arrays.hashCode(sliderTops);
        }

        @Override
        public String toString() {
            return "Paint[knobs=" + Arrays.toString(knobs) + ", bottoms=" + Arrays.toString(sliderBottoms) + ", tops=" + Arrays.toString(sliderTops) + ", labels="
                    + Arrays.toString(labels) + ", logo=" + logo + "]";
        }
    }

    private VisualizerPainter() {
    }

    /**
     * @param allLights drive every light whatever {@link VisualizerConfig#getLights()} says (the profile's lighting
     *                  is an animation that can't be mixed with single lights)
     * @param positions per analog control (knobs, then sliders), 0..1 while it shows its position, else NaN
     * @param seconds   time, for the slowly turning rainbow
     */
    static Paint paint(VisualizerConfig cfg, Layout layout, Music music, float[] positions, double seconds, boolean allLights) {
        var style = cfg.getStyle() == null ? VisualizerStyle.RAINBOW : cfg.getStyle();
        var low = parse(cfg.getLowColor(), parse(VisualizerConfig.DEFAULT_LOW, 0));
        var high = parse(cfg.getHighColor(), parse(VisualizerConfig.DEFAULT_HIGH, 0));
        var pulse = style == VisualizerStyle.PULSE;
        var bands = pulse ? filled(music.overall()) : music.bands();
        var lights = allLights || ArrayUtils.isEmpty(cfg.getLights()) ? null : cfg.getLights();

        var knobs = new String[layout.knobs()];
        for (var i = 0; i < knobs.length; i++) {
            if (!drives(lights, "knob:" + i)) {
                continue;
            }
            var band = knobBand(i, layout.knobs());
            var level = band == BAND_OVERALL ? music.overall() : bands[band];
            var position = position(positions, i);
            var color = colorOf(style, band, Float.isNaN(position) ? level : position, low, high, seconds);
            knobs[i] = Float.isNaN(position)
                    ? hex(mix(scale(color, 0.06f + 0.94f * level), WHITE, music.glow() * 0.6f))
                    : hex(scale(color, 0.06f + 0.94f * position));
        }

        var bottoms = new String[layout.sliders()];
        var tops = new String[layout.sliders()];
        var labels = new String[layout.labels() ? layout.sliders() : 0];
        for (var s = 0; s < layout.sliders(); s++) {
            var band = s % BandAnalyzer.BANDS;
            var position = position(positions, layout.knobs() + s);
            var level = Float.isNaN(position) ? bands[band] : position;
            int bottom;
            int top;
            switch (style) {
                case PULSE -> {
                    bottom = high;
                    top = high;
                }
                case RAINBOW -> {
                    bottom = hsv(hue(band, seconds));
                    top = hsv(hue(band, seconds) + 40);
                }
                default -> {
                    bottom = low;
                    top = high;
                }
            }
            if (drives(lights, "slider:" + s)) {
                bottoms[s] = hex(scale(bottom, Math.min(1, 2 * level)));
                tops[s] = hex(scale(top, Math.max(0, 2 * level - 1)));
            }
            if (layout.labels() && drives(lights, "label:" + s)) {
                labels[s] = hex(scale(bottom, 0.15f + 0.85f * level));
            }
        }

        String logo = null;
        if (layout.logo() && drives(lights, "logo")) {
            var color = style == VisualizerStyle.RAINBOW ? hsv(seconds * 30) : high;
            logo = hex(mix(scale(color, 0.1f + 0.9f * music.overall()), WHITE, music.glow() * 0.6f));
        }
        return new Paint(knobs, bottoms, tops, labels, logo);
    }

    /** The band a knob shows: five knobs put the whole mix in the middle, others take the bands in turn. */
    static int knobBand(int knob, int knobs) {
        if (knobs == 5) {
            return switch (knob) {
                case 0, 1 -> knob;
                case 2 -> BAND_OVERALL;
                default -> knob - 1;
            };
        }
        return knob % BandAnalyzer.BANDS;
    }

    private static boolean drives(@Nullable String[] lights, String key) {
        return lights == null || ArrayUtils.contains(lights, key);
    }

    private static float position(float[] positions, int index) {
        return index < positions.length ? positions[index] : Float.NaN;
    }

    private static float[] filled(float value) {
        var out = new float[BandAnalyzer.BANDS];
        Arrays.fill(out, value);
        return out;
    }

    private static int colorOf(VisualizerStyle style, int band, float level, int low, int high, double seconds) {
        return switch (style) {
            case PULSE -> high;
            case RAINBOW -> band == BAND_OVERALL ? hsv(hue(1.5, seconds) + 180) : hsv(hue(band, seconds));
            case TWO_COLORS -> mix(low, high, level);
        };
    }

    /** Band hues from red (bass) towards violet (treble), turning slowly. */
    private static double hue(double band, double seconds) {
        return band * 75 + seconds * 12;
    }

    /** A fully saturated, full-value colour at {@code degrees}. */
    static int hsv(double degrees) {
        var h = ((degrees % 360) + 360) % 360 / 60;
        var x = 1 - Math.abs(h % 2 - 1);
        double r;
        double g;
        double b;
        switch ((int) h) {
            case 0 -> { r = 1; g = x; b = 0; }
            case 1 -> { r = x; g = 1; b = 0; }
            case 2 -> { r = 0; g = 1; b = x; }
            case 3 -> { r = 0; g = x; b = 1; }
            case 4 -> { r = x; g = 0; b = 1; }
            default -> { r = 1; g = 0; b = x; }
        }
        return (int) Math.round(r * 255) << 16 | (int) Math.round(g * 255) << 8 | (int) Math.round(b * 255);
    }

    static int scale(int rgb, float factor) {
        var f = Math.clamp(factor, 0f, 1f);
        return Math.round(((rgb >> 16) & 0xFF) * f) << 16 | Math.round(((rgb >> 8) & 0xFF) * f) << 8 | Math.round((rgb & 0xFF) * f);
    }

    static int mix(int a, int b, float t) {
        var f = Math.clamp(t, 0f, 1f);
        var out = 0;
        for (var shift = 16; shift >= 0; shift -= 8) {
            var from = (a >> shift) & 0xFF;
            var to = (b >> shift) & 0xFF;
            out |= Math.round(from + (to - from) * f) << shift;
        }
        return out;
    }

    private static final char[] DIGITS = "0123456789ABCDEF".toCharArray();

    static String hex(int rgb) {
        var out = new char[7];
        out[0] = '#';
        for (var i = 6; i >= 1; i--) {
            out[i] = DIGITS[rgb & 0xF];
            rgb >>= 4;
        }
        return new String(out);
    }

    /** {@code #RRGGBB} (the # optional) as an int, or {@code fallback}. */
    static int parse(@Nullable String color, int fallback) {
        var digits = StringUtils.removeStart(StringUtils.trimToEmpty(color), "#");
        if (digits.length() != 6) {
            return fallback;
        }
        try {
            return Integer.parseInt(digits, 16);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
