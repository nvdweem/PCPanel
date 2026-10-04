package com.getpcpanel.device.provider.pcpanel;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import com.getpcpanel.profile.dto.SingleKnobLightingConfig;
import com.getpcpanel.profile.dto.SingleKnobLightingConfig.SINGLE_KNOB_MODE;
import com.getpcpanel.profile.dto.SingleLogoLightingConfig;
import com.getpcpanel.profile.dto.SingleLogoLightingConfig.SINGLE_LOGO_MODE;
import com.getpcpanel.profile.dto.SingleSliderLightingConfig;
import com.getpcpanel.profile.dto.SingleSliderLightingConfig.SINGLE_SLIDER_MODE;

/** A light with a brightness of its own (a notification light's override) shows at it; the others at the panel's. */
class OutputInterpreterBrightnessTest {
    private static final int PANEL = 10;
    /** 0xFF at the panel's 10 %. */
    private static final int DIM = 25;

    private static int[] unsigned(byte[] data, int from, int to) {
        var out = new int[to - from];
        for (var i = from; i < to; i++) {
            out[i - from] = data[i] & 0xFF;
        }
        return out;
    }

    private static SingleKnobLightingConfig knob(String color, Integer brightness) {
        return new SingleKnobLightingConfig().setMode(SINGLE_KNOB_MODE.STATIC).setColor1(color).setOverrideBrightness(brightness);
    }

    @Test
    void aKnobWithItsOwnBrightnessIgnoresThePanels() {
        var data = OutputInterpreter.buildKnobData((byte) 5, PANEL, new SingleKnobLightingConfig[] {
                knob("#FFFFFF", null), knob("#FF0000", 100), knob("#FFFFFF", null), knob("#00FF00", 50) });

        assertArrayEquals(new int[] { 1, DIM, DIM, DIM }, unsigned(data, 2, 6), "the panel's brightness");
        assertArrayEquals(new int[] { 1, 0xFF, 0, 0 }, unsigned(data, 9, 13), "full brightness");
        assertArrayEquals(new int[] { 1, DIM, DIM, DIM }, unsigned(data, 16, 20), "back to the panel's");
        assertArrayEquals(new int[] { 1, 0, 127, 0 }, unsigned(data, 23, 27), "half brightness");
    }

    @Test
    void aSliderWithItsOwnBrightnessIgnoresThePanels() {
        var data = OutputInterpreter.buildSliderData(PANEL, new SingleSliderLightingConfig[] {
                new SingleSliderLightingConfig().setMode(SINGLE_SLIDER_MODE.STATIC).setColor1("#FFFFFF").setOverrideBrightness(100),
                new SingleSliderLightingConfig().setMode(SINGLE_SLIDER_MODE.STATIC).setColor1("#FFFFFF") });

        assertArrayEquals(new int[] { 1, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF }, unsigned(data, 2, 9));
        assertArrayEquals(new int[] { 1, DIM, DIM, DIM, DIM, DIM, DIM }, unsigned(data, 9, 16));
    }

    @Test
    void theLogoWithItsOwnBrightnessIgnoresThePanels() {
        var bright = OutputInterpreter.buildLogoData(PANEL, new SingleLogoLightingConfig().setMode(SINGLE_LOGO_MODE.STATIC).setColor("#FFFFFF").setOverrideBrightness(100));
        var dim = OutputInterpreter.buildLogoData(PANEL, new SingleLogoLightingConfig().setMode(SINGLE_LOGO_MODE.STATIC).setColor("#FFFFFF"));

        assertArrayEquals(new int[] { 1, 0xFF, 0xFF, 0xFF }, unsigned(bright, 2, 6));
        assertArrayEquals(new int[] { 1, DIM, DIM, DIM }, unsigned(dim, 2, 6));
    }

    @Test
    void anRgbKnobWithItsOwnBrightnessIgnoresThePanels() {
        var data = OutputInterpreter.buildRGBCustomData(PANEL, new SingleKnobLightingConfig[] {
                knob("#FFFFFF", null), knob("#FFFFFF", 100), knob("#FFFFFF", null), knob("#FFFFFF", null) });

        assertArrayEquals(new int[] { 1, DIM, DIM, DIM }, unsigned(data, 2, 6));
        assertArrayEquals(new int[] { 1, 0xFF, 0xFF, 0xFF }, unsigned(data, 6, 10));
        assertArrayEquals(new int[] { 1, DIM, DIM, DIM }, unsigned(data, 10, 14));
    }

    @Test
    void perLightBrightnessIsOptionalForTheRgbsSingleColours() {
        var colors = new String[] { "#FFFFFF", "#FFFFFF" };
        var none = OutputInterpreter.buildFullLEDData(PANEL, colors, null, new boolean[2]);
        var one = OutputInterpreter.buildFullLEDData(PANEL, colors, new Integer[] { 100, null }, new boolean[2]);

        assertArrayEquals(new int[] { 1, DIM, DIM, DIM, 1, DIM, DIM, DIM }, unsigned(none, 2, 10));
        assertArrayEquals(new int[] { 1, 0xFF, 0xFF, 0xFF, 1, DIM, DIM, DIM }, unsigned(one, 2, 10));
        assertArrayEquals(Arrays.copyOfRange(none, 10, 12), Arrays.copyOfRange(one, 10, 12), "volume tracking is unchanged");
    }
}
