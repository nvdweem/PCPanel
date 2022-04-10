package save;

import javafx.scene.paint.Color;
import main.DeviceType;
import util.Util;

public class LightingConfig {
    private LightingMode lightingMode;

    private String[] individualColors;

    private boolean[] volumeBrightnessTrackingEnabled;

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

    private SingleKnobLightingConfig[] knobConfigs;

    private SingleSliderLabelLightingConfig[] sliderLabelConfigs;

    private SingleSliderLightingConfig[] sliderConfigs;

    private SingleLogoLightingConfig logoConfig;

    public enum LightingMode {
        ALL_COLOR, ALL_RAINBOW, ALL_WAVE, ALL_BREATH, SINGLE_COLOR, CUSTOM
    }

    private LightingConfig() {
    }

    public LightingConfig(int numKnobs, int numSliders) {
        knobConfigs = new SingleKnobLightingConfig[numKnobs];
        int i;
        for (i = 0; i < knobConfigs.length; ) {
            knobConfigs[i] = new SingleKnobLightingConfig();
            i++;
        }
        sliderLabelConfigs = new SingleSliderLabelLightingConfig[numSliders];
        for (i = 0; i < sliderLabelConfigs.length; ) {
            sliderLabelConfigs[i] = new SingleSliderLabelLightingConfig();
            i++;
        }
        sliderConfigs = new SingleSliderLightingConfig[numSliders];
        for (i = 0; i < sliderConfigs.length; ) {
            sliderConfigs[i] = new SingleSliderLightingConfig();
            i++;
        }
        logoConfig = new SingleLogoLightingConfig();
    }

    public static LightingConfig defaultLightingConfig(DeviceType dt) {
        if (dt == DeviceType.PCPANEL_RGB) {
            LightingConfig lc = new LightingConfig();
            lc.lightingMode = LightingMode.ALL_COLOR;
            lc.allColor = "#0065FF";
            return lc;
        }
        if (dt == DeviceType.PCPANEL_MINI)
            return createRainbowAnimation((byte) -128, (byte) -1, (byte) -106, false, false);
        if (dt == DeviceType.PCPANEL_PRO) {
            LightingConfig lc = new LightingConfig(5, 4);
            lc.lightingMode = LightingMode.ALL_RAINBOW;
            lc.setRainbowPhaseShift((byte) 125);
            lc.setRainbowSpeed((byte) -96);
            lc.setRainbowBrightness((byte) -1);
            return lc;
        }
        throw new IllegalArgumentException("unknown deviceType");
    }

    public static LightingConfig createSingleColor(Color[] color, boolean[] volumeTracking) {
        LightingConfig lc = new LightingConfig();
        lc.lightingMode = LightingMode.SINGLE_COLOR;
        lc.individualColors = new String[color.length];
        for (int i = 0; i < color.length; i++)
            lc.individualColors[i] = Util.formatHexString(color[i]);
        lc.volumeBrightnessTrackingEnabled = volumeTracking;
        return lc;
    }

    public static LightingConfig createAllColor(Color color, boolean[] volumeTracking) {
        LightingConfig lc = new LightingConfig();
        lc.lightingMode = LightingMode.ALL_COLOR;
        lc.allColor = Util.formatHexString(color);
        lc.volumeBrightnessTrackingEnabled = volumeTracking;
        return lc;
    }

    public static LightingConfig createAllColor(Color color) {
        LightingConfig lc = new LightingConfig();
        lc.lightingMode = LightingMode.ALL_COLOR;
        lc.allColor = Util.formatHexString(color);
        return lc;
    }

    public static LightingConfig createRainbowAnimation(byte phaseShift, byte brightness, byte speed, boolean reverse) {
        LightingConfig lc = new LightingConfig();
        lc.lightingMode = LightingMode.ALL_RAINBOW;
        lc.rainbowPhaseShift = phaseShift;
        lc.rainbowBrightness = brightness;
        lc.rainbowSpeed = speed;
        lc.rainbowReverse = (byte) (reverse ? 1 : 0);
        return lc;
    }

    public static LightingConfig createRainbowAnimation(byte phaseShift, byte brightness, byte speed, boolean reverse, boolean vertical) {
        LightingConfig lc = new LightingConfig();
        lc.lightingMode = LightingMode.ALL_RAINBOW;
        lc.rainbowPhaseShift = phaseShift;
        lc.rainbowBrightness = brightness;
        lc.rainbowSpeed = speed;
        lc.rainbowReverse = (byte) (reverse ? 1 : 0);
        lc.rainbowVertical = (byte) (vertical ? 1 : 0);
        return lc;
    }

    public static LightingConfig createWaveAnimation(byte hue, byte brightness, byte speed, boolean reverse, boolean bounce) {
        LightingConfig lc = new LightingConfig();
        lc.lightingMode = LightingMode.ALL_WAVE;
        lc.waveHue = hue;
        lc.waveBrightness = brightness;
        lc.waveSpeed = speed;
        lc.waveReverse = (byte) (reverse ? 1 : 0);
        lc.waveBounce = (byte) (bounce ? 1 : 0);
        return lc;
    }

    public static LightingConfig createBreathAnimation(byte hue, byte brightness, byte speed) {
        LightingConfig lc = new LightingConfig();
        lc.lightingMode = LightingMode.ALL_BREATH;
        lc.breathHue = hue;
        lc.breathBrightness = brightness;
        lc.breathSpeed = speed;
        return lc;
    }

    public LightingMode getLightingMode() {
        return lightingMode;
    }

    public void setLightingMode(LightingMode lightingMode) {
        this.lightingMode = lightingMode;
    }

    public String[] getIndividualColors() {
        return individualColors;
    }

    public void setIndividualColors(String[] individualColors) {
        this.individualColors = individualColors;
    }

    public String getAllColor() {
        return allColor;
    }

    public void setAllColor(String allColor) {
        this.allColor = allColor;
    }

    public boolean[] getVolumeBrightnessTrackingEnabled() {
        if (volumeBrightnessTrackingEnabled == null)
            volumeBrightnessTrackingEnabled = new boolean[0];
        return volumeBrightnessTrackingEnabled;
    }

    public byte getRainbowPhaseShift() {
        return rainbowPhaseShift;
    }

    public byte getRainbowBrightness() {
        return rainbowBrightness;
    }

    public byte getRainbowSpeed() {
        return rainbowSpeed;
    }

    public byte getRainbowReverse() {
        return rainbowReverse;
    }

    public byte getRainbowVertical() {
        return rainbowVertical;
    }

    public void setRainbowVertical(byte rainbowVertical) {
        this.rainbowVertical = rainbowVertical;
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

    public void setRainbowReverse(byte rainbowReverse) {
        this.rainbowReverse = rainbowReverse;
    }

    public byte getWaveHue() {
        return waveHue;
    }

    public byte getWaveBrightness() {
        return waveBrightness;
    }

    public byte getWaveSpeed() {
        return waveSpeed;
    }

    public byte getWaveReverse() {
        return waveReverse;
    }

    public byte getWaveBounce() {
        return waveBounce;
    }

    public byte getBreathHue() {
        return breathHue;
    }

    public byte getBreathBrightness() {
        return breathBrightness;
    }

    public byte getBreathSpeed() {
        return breathSpeed;
    }

    public SingleKnobLightingConfig[] getKnobConfigs() {
        return knobConfigs;
    }

    public SingleSliderLabelLightingConfig[] getSliderLabelConfigs() {
        return sliderLabelConfigs;
    }

    public SingleSliderLightingConfig[] getSliderConfigs() {
        return sliderConfigs;
    }

    public SingleLogoLightingConfig getLogoConfig() {
        return logoConfig;
    }
}

