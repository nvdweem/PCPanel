package com.getpcpanel.profile.dto;

import java.util.Arrays;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.getpcpanel.device.DeviceType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@AllArgsConstructor
@Builder(toBuilder = true)
@JsonAutoDetect(fieldVisibility = Visibility.ANY)
public class LightingConfig {
    private LightingMode lightingMode;
    private String[] individualColors = {};
    private boolean[] volumeBrightnessTrackingEnabled = {};
    private String allColor;
    private byte rainbowPhaseShift;
    private byte rainbowBrightness;
    private byte rainbowSpeed;
    private byte rainbowReverse;
    private byte rainbowVertical;
    private byte waveHue;
    private byte waveBrightness;
    private byte waveSpeed;
    private byte waveReverse;
    private byte waveBounce;
    private byte breathHue;
    private byte breathBrightness;
    private byte breathSpeed;
    private SingleKnobLightingConfig[] knobConfigs = {};
    private SingleSliderLabelLightingConfig[] sliderLabelConfigs = {};
    private SingleSliderLightingConfig[] sliderConfigs = {};
    private SingleLogoLightingConfig logoConfig;
    @Getter @Setter private int globalBrightness = 100;

    public LightingConfig deepCopy() {
        return toBuilder()
                .knobConfigs(knobConfigs == null ? null : Arrays.copyOf(knobConfigs, knobConfigs.length))
                .sliderLabelConfigs(sliderLabelConfigs == null ? null : Arrays.copyOf(sliderLabelConfigs, sliderLabelConfigs.length))
                .sliderConfigs(sliderConfigs == null ? null : Arrays.copyOf(sliderConfigs, sliderConfigs.length))
                .build();
    }

    public enum LightingMode {
        ALL_COLOR, ALL_RAINBOW, ALL_WAVE, ALL_BREATH, SINGLE_COLOR, CUSTOM
    }

    private LightingConfig() {
    }

    public LightingConfig(int numKnobs, int numSliders) {
        knobConfigs = new SingleKnobLightingConfig[numKnobs];
        for (var i = 0; i < knobConfigs.length; i++) {
            knobConfigs[i] = new SingleKnobLightingConfig();
        }
        sliderLabelConfigs = new SingleSliderLabelLightingConfig[numSliders];
        for (var i = 0; i < sliderLabelConfigs.length; i++) {
            sliderLabelConfigs[i] = new SingleSliderLabelLightingConfig();
        }
        sliderConfigs = new SingleSliderLightingConfig[numSliders];
        for (var i = 0; i < sliderConfigs.length; i++) {
            sliderConfigs[i] = new SingleSliderLightingConfig();
        }
        logoConfig = new SingleLogoLightingConfig();
    }

    @SuppressWarnings("MagicNumber")
    public static LightingConfig defaultLightingConfig(DeviceType dt) {
        if (dt == DeviceType.PCPANEL_RGB) {
            var lc = new LightingConfig();
            lc.lightingMode = LightingMode.ALL_COLOR;
            lc.allColor = "#0065FF";
            return lc;
        }
        if (dt == DeviceType.PCPANEL_MINI)
            return createRainbowAnimation((byte) -128, (byte) -1, (byte) -106, false, false);
        if (dt == DeviceType.PCPANEL_PRO) {
            var lc = new LightingConfig(5, 4);
            lc.lightingMode = LightingMode.ALL_RAINBOW;
            lc.setRainbowPhaseShift((byte) 125);
            lc.setRainbowSpeed((byte) -96);
            lc.setRainbowBrightness((byte) -1);
            return lc;
        }
        throw new IllegalArgumentException("unknown deviceType");
    }

    public static LightingConfig createRainbowAnimation(byte phaseShift, byte brightness, byte speed, boolean reverse) {
        var lc = new LightingConfig();
        lc.lightingMode = LightingMode.ALL_RAINBOW;
        lc.rainbowPhaseShift = phaseShift;
        lc.rainbowBrightness = brightness;
        lc.rainbowSpeed = speed;
        lc.rainbowReverse = (byte) (reverse ? 1 : 0);
        return lc;
    }

    public static LightingConfig createRainbowAnimation(byte phaseShift, byte brightness, byte speed, boolean reverse, boolean vertical) {
        var lc = new LightingConfig();
        lc.lightingMode = LightingMode.ALL_RAINBOW;
        lc.rainbowPhaseShift = phaseShift;
        lc.rainbowBrightness = brightness;
        lc.rainbowSpeed = speed;
        lc.rainbowReverse = (byte) (reverse ? 1 : 0);
        lc.rainbowVertical = (byte) (vertical ? 1 : 0);
        return lc;
    }

    public static LightingConfig createWaveAnimation(byte hue, byte brightness, byte speed, boolean reverse, boolean bounce) {
        var lc = new LightingConfig();
        lc.lightingMode = LightingMode.ALL_WAVE;
        lc.waveHue = hue;
        lc.waveBrightness = brightness;
        lc.waveSpeed = speed;
        lc.waveReverse = (byte) (reverse ? 1 : 0);
        lc.waveBounce = (byte) (bounce ? 1 : 0);
        return lc;
    }

    public static LightingConfig createBreathAnimation(byte hue, byte brightness, byte speed) {
        var lc = new LightingConfig();
        lc.lightingMode = LightingMode.ALL_BREATH;
        lc.breathHue = hue;
        lc.breathBrightness = brightness;
        lc.breathSpeed = speed;
        return lc;
    }

    public static LightingConfig createAllColor(String color) {
        var lc = new LightingConfig();
        lc.lightingMode = LightingMode.ALL_COLOR;
        lc.allColor = color;
        return lc;
    }

    public LightingMode lightingMode() {
        return lightingMode;
    }

    public void setLightingMode(LightingMode lightingMode) {
        this.lightingMode = lightingMode;
    }

    public String[] individualColors() {
        return individualColors;
    }

    public String allColor() {
        return allColor;
    }

    public boolean[] volumeBrightnessTrackingEnabled() {
        if (volumeBrightnessTrackingEnabled == null)
            volumeBrightnessTrackingEnabled = new boolean[0];
        return volumeBrightnessTrackingEnabled;
    }

    public byte rainbowPhaseShift() {
        return rainbowPhaseShift;
    }

    public byte rainbowBrightness() {
        return rainbowBrightness;
    }

    public byte rainbowSpeed() {
        return rainbowSpeed;
    }

    public byte rainbowReverse() {
        return rainbowReverse;
    }

    public byte rainbowVertical() {
        return rainbowVertical;
    }

    public void setRainbowPhaseShift(byte rainbowPhaseShift) {
        this.rainbowPhaseShift = rainbowPhaseShift;
    }

    public void setRainbowBrightness(byte rainbowBrightness) {
        this.rainbowBrightness = rainbowBrightness;
    }

    public void setRainbowSpeed(byte rainbowSpeed) {
        this.rainbowSpeed = rainbowSpeed;
    }

    public byte waveHue() {
        return waveHue;
    }

    public byte waveBrightness() {
        return waveBrightness;
    }

    public byte waveSpeed() {
        return waveSpeed;
    }

    public byte waveReverse() {
        return waveReverse;
    }

    public byte waveBounce() {
        return waveBounce;
    }

    public byte breathHue() {
        return breathHue;
    }

    public byte breathBrightness() {
        return breathBrightness;
    }

    public byte breathSpeed() {
        return breathSpeed;
    }

    public SingleKnobLightingConfig[] knobConfigs() {
        return knobConfigs;
    }

    public SingleSliderLabelLightingConfig[] sliderLabelConfigs() {
        return sliderLabelConfigs;
    }

    public SingleSliderLightingConfig[] sliderConfigs() {
        return sliderConfigs;
    }

    public SingleLogoLightingConfig logoConfig() {
        if (logoConfig == null) {
            logoConfig = new SingleLogoLightingConfig();
        }
        return logoConfig;
    }
}
