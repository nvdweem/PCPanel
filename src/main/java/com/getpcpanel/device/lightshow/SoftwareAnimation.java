package com.getpcpanel.device.lightshow;

import java.util.function.IntFunction;

import com.getpcpanel.device.lightshow.LightShow.Layout;
import com.getpcpanel.profile.dto.LightingConfig;
import com.getpcpanel.profile.dto.LightingConfig.LightingMode;
import com.getpcpanel.profile.dto.SingleKnobLightingConfig.SINGLE_KNOB_MODE;

/**
 * A device's whole-panel lighting (solid colour, a colour per light, rainbow, wave, breath) drawn in software as
 * per-control frames, so colour overrides can be painted on top of it. Lights are numbered knobs first, then sliders,
 * then the logo. A frame keeps the lighting's global brightness; per-control lighting is returned as it is.
 */
public final class SoftwareAnimation {
    private static final double HUES = 256;

    private SoftwareAnimation() {
    }

    /** How long one cycle of an animation takes at {@code speed} (0 slowest, 255 fastest). */
    public static long periodMs(byte speed) {
        return Math.max(500, 10_000 - (speed & 0xFF) * 37L);
    }

    /** Whether {@code mode} looks the same at every moment, so one frame shows it. */
    public static boolean isStill(LightingMode mode) {
        return mode == LightingMode.ALL_COLOR || mode == LightingMode.SINGLE_COLOR || mode == LightingMode.CUSTOM;
    }

    /** {@code base} at {@code nowMs} as per-control lighting for a device with {@code layout}'s lights. */
    public static LightingConfig frame(LightingConfig base, Layout layout, long nowMs) {
        var mode = base.lightingMode();
        if (mode == null || mode == LightingMode.CUSTOM) {
            return base;
        }
        var frame = switch (mode) {
            case ALL_COLOR -> solid(layout, base, p -> base.allColor());
            case SINGLE_COLOR -> solid(layout, base, p -> {
                var colors = base.individualColors();
                return colors != null && p < colors.length && colors[p] != null ? colors[p] : "#000000";
            });
            case ALL_RAINBOW -> rainbow(base, layout, nowMs);
            case ALL_WAVE -> wave(base, layout, nowMs);
            case ALL_BREATH -> breath(base, layout, nowMs);
            case CUSTOM -> base;
        };
        frame.setGlobalBrightness(base.getGlobalBrightness());
        return frame;
    }

    /** A still frame; knobs set to follow the volume keep doing so, in their colour. */
    private static LightingConfig solid(Layout layout, LightingConfig base, IntFunction<String> colorOf) {
        var frame = Animations.frame(layout, colorOf);
        var tracking = base.volumeBrightnessTrackingEnabled();
        for (var i = 0; i < frame.knobConfigs().length && i < tracking.length; i++) {
            if (tracking[i]) {
                var knob = frame.knobConfigs()[i];
                knob.setColor2(knob.getColor1());
                knob.setColor1("#000000");
                knob.setMode(SINGLE_KNOB_MODE.VOLUME_GRADIENT);
            }
        }
        return frame;
    }

    private static LightingConfig rainbow(LightingConfig base, Layout layout, long nowMs) {
        var n = layout.count();
        var travelled = HUES * nowMs / periodMs(base.rainbowSpeed());
        var shift = base.rainbowReverse() != 0 ? -travelled : travelled;
        var value = (base.rainbowBrightness() & 0xFF) / 255.0;
        return Animations.frame(layout, p -> Animations.hsv(wrap(((base.rainbowPhaseShift() & 0xFF) + p * HUES / n + shift) / HUES), 1, value));
    }

    private static LightingConfig wave(LightingConfig base, Layout layout, long nowMs) {
        var n = layout.count();
        var cycles = nowMs / (double) periodMs(base.waveSpeed());
        if (base.waveBounce() != 0) {
            var turn = cycles % 2;
            cycles = turn <= 1 ? turn : 2 - turn;
        }
        var head = base.waveReverse() != 0 ? -cycles : cycles;
        var hue = (base.waveHue() & 0xFF) / HUES;
        var value = (base.waveBrightness() & 0xFF) / 255.0;
        return Animations.frame(layout, p -> Animations.hsv(hue, 1, value * (0.5 + 0.5 * Math.cos(2 * Math.PI * (p / (double) n - head)))));
    }

    private static LightingConfig breath(LightingConfig base, Layout layout, long nowMs) {
        var cycles = nowMs / (double) periodMs(base.breathSpeed());
        var value = (base.breathBrightness() & 0xFF) / 255.0 * (0.5 - 0.5 * Math.cos(2 * Math.PI * cycles));
        var color = Animations.hsv((base.breathHue() & 0xFF) / HUES, 1, value);
        return Animations.frame(layout, p -> color);
    }

    /** {@code x} brought into 0 (inclusive) to 1 (exclusive). */
    private static double wrap(double x) {
        var w = x - Math.floor(x);
        return w >= 1 ? 0 : w;
    }
}
