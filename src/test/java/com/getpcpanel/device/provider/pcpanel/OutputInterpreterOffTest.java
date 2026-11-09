package com.getpcpanel.device.provider.pcpanel;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import org.junit.jupiter.api.Test;

import com.getpcpanel.profile.dto.SingleKnobLightingConfig;
import com.getpcpanel.profile.dto.SingleKnobLightingConfig.SINGLE_KNOB_MODE;
import com.getpcpanel.profile.dto.SingleLogoLightingConfig;
import com.getpcpanel.profile.dto.SingleLogoLightingConfig.SINGLE_LOGO_MODE;
import com.getpcpanel.profile.dto.SingleSliderLightingConfig;
import com.getpcpanel.profile.dto.SingleSliderLightingConfig.SINGLE_SLIDER_MODE;

/** A light that is off goes out as static black: the panel keeps a light's last colour for an empty entry. */
class OutputInterpreterOffTest {
    private static final int PANEL = 100;

    private static int[] unsigned(byte[] data, int from, int to) {
        var out = new int[to - from];
        for (var i = from; i < to; i++) {
            out[i - from] = data[i] & 0xFF;
        }
        return out;
    }

    @Test
    void aKnobThatIsOffIsSentAsStaticBlack() {
        var data = OutputInterpreter.buildKnobData((byte) 5, PANEL, new SingleKnobLightingConfig[] {
                new SingleKnobLightingConfig().setMode(SINGLE_KNOB_MODE.STATIC).setColor1("#FF0000"),
                new SingleKnobLightingConfig().setMode(SINGLE_KNOB_MODE.NONE) });

        assertArrayEquals(new int[] { 1, 0xFF, 0, 0 }, unsigned(data, 2, 6), "the lit knob");
        assertArrayEquals(new int[] { 1, 0, 0, 0, 0, 0, 0 }, unsigned(data, 9, 16), "the knob that is off");
    }

    @Test
    void aSliderThatIsOffIsSentAsStaticBlack() {
        var data = OutputInterpreter.buildSliderData(PANEL, new SingleSliderLightingConfig[] {
                new SingleSliderLightingConfig().setMode(SINGLE_SLIDER_MODE.NONE) });

        assertArrayEquals(new int[] { 1, 0, 0, 0, 0, 0, 0 }, unsigned(data, 2, 9));
    }

    @Test
    void theLogoWhenOffIsSentAsStaticBlack() {
        var data = OutputInterpreter.buildLogoData(PANEL, new SingleLogoLightingConfig().setMode(SINGLE_LOGO_MODE.NONE));

        assertArrayEquals(new int[] { 1, 0, 0, 0 }, unsigned(data, 2, 6));
    }
}
