package com.getpcpanel.device.lightshow;

import java.util.function.IntFunction;

import com.getpcpanel.device.lightshow.LightShow.Animation;
import com.getpcpanel.device.lightshow.LightShow.Layout;
import com.getpcpanel.profile.dto.LightingConfig;
import com.getpcpanel.profile.dto.LightingConfig.LightingMode;
import com.getpcpanel.profile.dto.SingleKnobLightingConfig;
import com.getpcpanel.profile.dto.SingleKnobLightingConfig.SINGLE_KNOB_MODE;
import com.getpcpanel.profile.dto.SingleLogoLightingConfig;
import com.getpcpanel.profile.dto.SingleLogoLightingConfig.SINGLE_LOGO_MODE;
import com.getpcpanel.profile.dto.SingleSliderLabelLightingConfig;
import com.getpcpanel.profile.dto.SingleSliderLabelLightingConfig.SINGLE_SLIDER_LABEL_MODE;
import com.getpcpanel.profile.dto.SingleSliderLightingConfig;
import com.getpcpanel.profile.dto.SingleSliderLightingConfig.SINGLE_SLIDER_MODE;

/** The light shows the app plays. Lights are numbered knobs first, then sliders, then the logo. */
public final class Animations {
    public static final long STARTUP_MS = 2_400;
    private static final String[] TEST_COLORS = { "#FF0000", "#00FF00", "#0000FF", "#FFFFFF" };
    private static final double TEST_COLOR_SHARE = 0.4;

    private Animations() {
    }

    /**
     * Start-up: a white-hot spark runs across the knobs and up the sliders to the logo, each light it passes cooling
     * into its own colour of the rainbow, then everything fades out and the panel's own lighting takes over.
     */
    public static Animation ignition() {
        return (t, layout) -> {
            var n = layout.count();
            var spark = t / 0.55 * (n + 1.5); // the head reaches past the logo just after halfway
            var fade = t < 0.75 ? 1 : Math.max(0, 1 - (t - 0.75) / 0.25);
            return frame(layout, p -> {
                var behind = spark - p;
                if (behind < 0) {
                    return "#000000";
                }
                var saturation = Math.min(1, behind / 1.8); // white at the head, full colour a little behind it
                var value = Math.min(1, behind * 2) * fade;
                return hsv(p / (double) n, saturation, value);
            });
        };
    }

    /** Length of {@link #selfTest()}: 0.6 s per colour, the rest for lighting each light on its own. */
    public static final long SELF_TEST_MS = Math.round(TEST_COLORS.length * 600 / TEST_COLOR_SHARE);

    /**
     * Self-test: every light red, green, blue and white in turn, then each light on its own in white, in order, so a
     * dead LED or a wrongly placed one stands out.
     */
    public static Animation selfTest() {
        return (t, layout) -> {
            if (t < TEST_COLOR_SHARE) {
                var color = TEST_COLORS[Math.min(TEST_COLORS.length - 1, (int) (t / TEST_COLOR_SHARE * TEST_COLORS.length))];
                return frame(layout, p -> color);
            }
            var lit = (int) ((t - TEST_COLOR_SHARE) / (1 - TEST_COLOR_SHARE) * layout.count());
            return frame(layout, p -> p == lit ? "#FFFFFF" : "#000000");
        };
    }

    /** A per-control frame with light {@code p} in {@code colorOf(p)}. */
    static LightingConfig frame(Layout layout, IntFunction<String> colorOf) {
        var lc = new LightingConfig(layout.knobs(), layout.sliders());
        for (var i = 0; i < layout.knobs(); i++) {
            lc.knobConfigs()[i] = new SingleKnobLightingConfig().setMode(SINGLE_KNOB_MODE.STATIC).setColor1(colorOf.apply(i));
        }
        for (var j = 0; j < layout.sliders(); j++) {
            var color = colorOf.apply(layout.knobs() + j);
            lc.sliderConfigs()[j] = new SingleSliderLightingConfig().setMode(SINGLE_SLIDER_MODE.STATIC).setColor1(color);
            lc.sliderLabelConfigs()[j] = new SingleSliderLabelLightingConfig().setMode(SINGLE_SLIDER_LABEL_MODE.STATIC).setColor(color);
        }
        var logo = new SingleLogoLightingConfig().setMode(SINGLE_LOGO_MODE.STATIC).setColor(colorOf.apply(layout.count() - 1));
        return lc.toBuilder().lightingMode(LightingMode.CUSTOM).globalBrightness(100).logoConfig(logo).build();
    }

    /** {@code #RRGGBB} for a hue (0..1), saturation and value. */
    static String hsv(double h, double s, double v) {
        var i = (int) Math.floor(h * 6) % 6;
        var f = h * 6 - Math.floor(h * 6);
        var p = v * (1 - s);
        var q = v * (1 - f * s);
        var u = v * (1 - (1 - f) * s);
        double r;
        double g;
        double b;
        switch (i) {
            case 0 -> { r = v; g = u; b = p; }
            case 1 -> { r = q; g = v; b = p; }
            case 2 -> { r = p; g = v; b = u; }
            case 3 -> { r = p; g = q; b = v; }
            case 4 -> { r = u; g = p; b = v; }
            default -> { r = v; g = p; b = q; }
        }
        return String.format("#%02X%02X%02X", Math.round(r * 255), Math.round(g * 255), Math.round(b * 255));
    }
}
