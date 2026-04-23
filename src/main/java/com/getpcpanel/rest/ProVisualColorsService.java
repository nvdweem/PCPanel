package com.getpcpanel.rest;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.getpcpanel.device.Device;
import com.getpcpanel.device.DeviceType;
import com.getpcpanel.profile.dto.LightingConfig;
import com.getpcpanel.profile.dto.SingleKnobLightingConfig;
import com.getpcpanel.profile.dto.SingleLogoLightingConfig;
import com.getpcpanel.profile.dto.SingleSliderLabelLightingConfig;
import com.getpcpanel.profile.dto.SingleSliderLightingConfig;
import com.getpcpanel.util.Util;
import com.getpcpanel.util.coloroverride.OverrideColorService;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class ProVisualColorsService {
    private static final String BLACK = "#000000";
    private static final int PRO_DIAL_COUNT = 5;
    private static final int PRO_SLIDER_COUNT = 4;
    private static final int PRO_SLIDER_SEGMENT_COUNT = 5;

    @Inject
    OverrideColorService overrideColorService;

    public ProVisualColors resolve(Device device) {
        if (device == null || device.deviceType() != DeviceType.PCPANEL_PRO) {
            return ProVisualColors.empty();
        }

        var config = device.lightingConfig();
        if (config == null || config.lightingMode() == null) {
            return ProVisualColors.defaultForPro();
        }

        return switch (config.lightingMode()) {
            case ALL_COLOR -> monochrome(colorOrDefault(config.allColor()));
            case ALL_WAVE -> fromWave(config);
            case ALL_BREATH -> monochrome(colorFromHue(config.breathHue(), config.breathBrightness()));
            case ALL_RAINBOW -> fromRainbow(config);
            case CUSTOM -> fromCustom(device.getSerialNumber(), config);
            default -> ProVisualColors.defaultForPro();
        };
    }

    private ProVisualColors fromRainbow(LightingConfig config) {
        var baseHue = unitByte(config.rainbowPhaseShift());
        var reverse = config.rainbowReverse() == 1;
        var vertical = config.rainbowVertical() == 1;
        var brightness = unitByte(config.rainbowBrightness());

        var dialColors = new ArrayList<String>(PRO_DIAL_COUNT);
        for (var i = 0; i < PRO_DIAL_COUNT; i++) {
            dialColors.add(rainbowColor(baseHue, reverse, i, PRO_DIAL_COUNT, brightness));
        }

        var sliderLabelColors = new ArrayList<String>(PRO_SLIDER_COUNT);
        for (var i = 0; i < PRO_SLIDER_COUNT; i++) {
            sliderLabelColors.add(rainbowColor(baseHue, reverse, i + PRO_DIAL_COUNT, PRO_DIAL_COUNT + PRO_SLIDER_COUNT, brightness));
        }

        var sliderColors = new ArrayList<List<String>>(PRO_SLIDER_COUNT);
        for (var s = 0; s < PRO_SLIDER_COUNT; s++) {
            var segmentColors = new ArrayList<String>(PRO_SLIDER_SEGMENT_COUNT);
            for (var seg = 0; seg < PRO_SLIDER_SEGMENT_COUNT; seg++) {
                var idx = vertical ? seg : (s * PRO_SLIDER_SEGMENT_COUNT + seg);
                var total = vertical ? PRO_SLIDER_SEGMENT_COUNT : (PRO_SLIDER_COUNT * PRO_SLIDER_SEGMENT_COUNT);
                segmentColors.add(rainbowColor(baseHue, reverse, idx, total, brightness));
            }
            sliderColors.add(List.copyOf(segmentColors));
        }

        var logoColor = rainbowColor(baseHue, reverse, PRO_DIAL_COUNT + PRO_SLIDER_COUNT, PRO_DIAL_COUNT + PRO_SLIDER_COUNT + 1, brightness);
        return new ProVisualColors(List.copyOf(dialColors), List.copyOf(sliderLabelColors), List.copyOf(sliderColors), logoColor);
    }

    private ProVisualColors fromWave(LightingConfig config) {
        var centerHue = unitByte(config.waveHue());
        var brightness = unitByte(config.waveBrightness());
        var reverse = config.waveReverse() == 1;
        var bounce = config.waveBounce() == 1;

        var dialColors = new ArrayList<String>(PRO_DIAL_COUNT);
        for (var i = 0; i < PRO_DIAL_COUNT; i++) {
            dialColors.add(waveColor(centerHue, brightness, i, PRO_DIAL_COUNT, reverse, bounce));
        }

        var sliderLabelColors = new ArrayList<String>(PRO_SLIDER_COUNT);
        for (var i = 0; i < PRO_SLIDER_COUNT; i++) {
            sliderLabelColors.add(waveColor(centerHue, brightness, i, PRO_SLIDER_COUNT, reverse, bounce));
        }

        var sliderColors = new ArrayList<List<String>>(PRO_SLIDER_COUNT);
        for (var s = 0; s < PRO_SLIDER_COUNT; s++) {
            var segmentColors = new ArrayList<String>(PRO_SLIDER_SEGMENT_COUNT);
            for (var seg = 0; seg < PRO_SLIDER_SEGMENT_COUNT; seg++) {
                segmentColors.add(waveColor(centerHue, brightness, seg, PRO_SLIDER_SEGMENT_COUNT, reverse, bounce));
            }
            sliderColors.add(List.copyOf(segmentColors));
        }

        var logoColor = colorFromHue(config.waveHue(), config.waveBrightness());
        return new ProVisualColors(List.copyOf(dialColors), List.copyOf(sliderLabelColors), List.copyOf(sliderColors), logoColor);
    }

    private ProVisualColors monochrome(String color) {
        var c = colorOrDefault(color);
        var dials = nCopies(PRO_DIAL_COUNT, c);
        var labels = nCopies(PRO_SLIDER_COUNT, c);
        var sliders = new ArrayList<List<String>>(PRO_SLIDER_COUNT);
        for (var i = 0; i < PRO_SLIDER_COUNT; i++) {
            sliders.add(nCopies(PRO_SLIDER_SEGMENT_COUNT, c));
        }
        return new ProVisualColors(dials, labels, List.copyOf(sliders), c);
    }

    private ProVisualColors fromCustom(String serial, LightingConfig config) {
        var dialColors = new ArrayList<String>(PRO_DIAL_COUNT);
        var knobConfigs = config.knobConfigs();
        for (var i = 0; i < PRO_DIAL_COUNT; i++) {
            var knob = i < knobConfigs.length ? knobConfigs[i] : new SingleKnobLightingConfig();
            knob = overrideColorService.getDialOverride(serial, i).orElse(knob);
            dialColors.add(resolveDialColor(knob));
        }

        var sliderLabelColors = new ArrayList<String>(PRO_SLIDER_COUNT);
        var labelConfigs = config.sliderLabelConfigs();
        for (var i = 0; i < PRO_SLIDER_COUNT; i++) {
            var label = i < labelConfigs.length ? labelConfigs[i] : new SingleSliderLabelLightingConfig();
            label = overrideColorService.getSliderLabelOverride(serial, i).orElse(label);
            sliderLabelColors.add(resolveSliderLabelColor(label));
        }

        var sliderColors = new ArrayList<List<String>>(PRO_SLIDER_COUNT);
        var sliderConfigs = config.sliderConfigs();
        for (var i = 0; i < PRO_SLIDER_COUNT; i++) {
            var slider = i < sliderConfigs.length ? sliderConfigs[i] : new SingleSliderLightingConfig();
            slider = overrideColorService.getSliderOverride(serial, i).orElse(slider);
            sliderColors.add(resolveSliderColors(slider));
        }

        var logo = overrideColorService.getLogoOverride(serial).orElse(config.logoConfig());
        var logoColor = resolveLogoColor(logo);

        return new ProVisualColors(List.copyOf(dialColors), List.copyOf(sliderLabelColors), List.copyOf(sliderColors), logoColor);
    }

    private String resolveDialColor(SingleKnobLightingConfig config) {
        if (config == null || config.getMode() == null) {
            return BLACK;
        }
        return switch (config.getMode()) {
            case NONE -> BLACK;
            case STATIC, VOLUME_GRADIENT -> firstColor(config.getColor1(), config.getColor2());
        };
    }

    private String resolveSliderLabelColor(SingleSliderLabelLightingConfig config) {
        if (config == null || config.getMode() == null) {
            return BLACK;
        }
        return switch (config.getMode()) {
            case NONE -> BLACK;
            case STATIC -> colorOrDefault(config.getColor());
        };
    }

    private List<String> resolveSliderColors(SingleSliderLightingConfig config) {
        if (config == null || config.getMode() == null) {
            return nCopies(PRO_SLIDER_SEGMENT_COUNT, BLACK);
        }

        return switch (config.getMode()) {
            case NONE -> nCopies(PRO_SLIDER_SEGMENT_COUNT, BLACK);
            case STATIC -> nCopies(PRO_SLIDER_SEGMENT_COUNT, colorOrDefault(config.getColor1()));
            case STATIC_GRADIENT, VOLUME_GRADIENT -> gradient(config.getColor1(), config.getColor2(), PRO_SLIDER_SEGMENT_COUNT);
        };
    }

    String resolveLogoColor(SingleLogoLightingConfig config) {
        if (config == null || config.getMode() == null) {
            return BLACK;
        }

        return switch (config.getMode()) {
            case NONE -> BLACK;
            case STATIC -> colorOrDefault(config.getColor());
            case RAINBOW -> "$RAINBOW!";
            case BREATH -> "$BREATH";
        };
    }

    private List<String> gradient(String startColor, String endColor, int steps) {
        var start = Util.parseColorComponents(colorOrDefault(startColor));
        var end = Util.parseColorComponents(colorOrDefault(endColor));

        if (start == null || end == null) {
            return nCopies(steps, BLACK);
        }

        var result = new ArrayList<String>(steps);
        for (var i = 0; i < steps; i++) {
            var ratio = steps == 1 ? 0f : (float) i / (steps - 1);
            var r = Math.round(start[0] + (end[0] - start[0]) * ratio);
            var g = Math.round(start[1] + (end[1] - start[1]) * ratio);
            var b = Math.round(start[2] + (end[2] - start[2]) * ratio);
            result.add(toHex(r, g, b));
        }
        return List.copyOf(result);
    }

    private String colorFromHue(byte hue, byte brightness) {
        return colorFromHsb(unitByte(hue), 1f, unitByte(brightness));
    }

    private String rainbowColor(float baseHue, boolean reverse, int index, int total, float brightness) {
        var span = 0.7f;
        var shift = total <= 1 ? 0f : (span * index / (total - 1));
        var hue = reverse ? baseHue - shift : baseHue + shift;
        return colorFromHsb(normalizeHue(hue), 1f, brightness);
    }

    private String waveColor(float centerHue, float brightness, int index, int total, boolean reverse, boolean bounce) {
        var progress = total <= 1 ? 0f : (float) index / (total - 1);
        if (reverse) {
            progress = 1f - progress;
        }
        var spread = 0.12f;
        var offset = bounce
                ? (Math.abs(progress - 0.5f) * 2f * spread)
                : ((progress - 0.5f) * 2f * spread);
        return colorFromHsb(normalizeHue(centerHue + offset), 1f, brightness);
    }

    private String colorFromHsb(float hue, float saturation, float brightness) {
        var rgb = Color.HSBtoRGB(hue, saturation, brightness);
        var r = (rgb >> 16) & 0xFF;
        var g = (rgb >> 8) & 0xFF;
        var b = rgb & 0xFF;
        return toHex(r, g, b);
    }

    private String toHex(int r, int g, int b) {
        var hex = Util.formatHexString(r, g, b);
        return hex == null ? BLACK : hex;
    }

    private float unitByte(byte value) {
        return (value & 0xFF) / 255f;
    }

    private float normalizeHue(float hue) {
        var normalized = hue % 1f;
        return normalized < 0 ? normalized + 1f : normalized;
    }

    private String firstColor(String color1, String color2) {
        var c1 = colorOrDefault(color1);
        if (!BLACK.equals(c1)) {
            return c1;
        }
        return colorOrDefault(color2);
    }

    private String colorOrDefault(String color) {
        var parsed = Util.parseColorComponents(color);
        if (parsed == null) {
            return BLACK;
        }
        return color.startsWith("#") ? color : "#" + color;
    }

    private static List<String> nCopies(int count, String color) {
        return Collections.nCopies(count, color);
    }

    public record ProVisualColors(
            List<String> dialColors,
            List<String> sliderLabelColors,
            List<List<String>> sliderColors,
            String logoColor
    ) {
        public static ProVisualColors empty() {
            return new ProVisualColors(List.of(), List.of(), List.of(), BLACK);
        }

        public static ProVisualColors defaultForPro() {
            var blackDials = nCopies(PRO_DIAL_COUNT, BLACK);
            var blackLabels = nCopies(PRO_SLIDER_COUNT, BLACK);
            var blackSliders = new ArrayList<List<String>>(PRO_SLIDER_COUNT);
            for (var i = 0; i < PRO_SLIDER_COUNT; i++) {
                blackSliders.add(nCopies(PRO_SLIDER_SEGMENT_COUNT, BLACK));
            }
            return new ProVisualColors(blackDials, blackLabels, List.copyOf(blackSliders), BLACK);
        }
    }
}
