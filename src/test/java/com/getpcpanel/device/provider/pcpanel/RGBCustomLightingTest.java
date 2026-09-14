package com.getpcpanel.device.provider.pcpanel;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import com.getpcpanel.profile.dto.SingleKnobLightingConfig;
import com.getpcpanel.profile.dto.SingleKnobLightingConfig.SINGLE_KNOB_MODE;

/**
 * The per-control ({@code CUSTOM}) lighting packet for the PCPanel RGB: a two-byte header, then
 * {@code [1, r, g, b]} per knob, then one volume-brightness-tracking flag per knob.
 */
class RGBCustomLightingTest {
    private static int[] unsigned(byte[] data) {
        var out = new int[data.length];
        for (var i = 0; i < data.length; i++) {
            out[i] = data[i] & 0xFF;
        }
        return out;
    }

    @Test
    void staticKnobsSendTheirOwnColours() {
        var knobs = new SingleKnobLightingConfig[] {
                new SingleKnobLightingConfig().setMode(SINGLE_KNOB_MODE.STATIC).setColor1("#FF0000"),
                new SingleKnobLightingConfig().setMode(SINGLE_KNOB_MODE.STATIC).setColor1("#00FF00"),
                new SingleKnobLightingConfig().setMode(SINGLE_KNOB_MODE.STATIC).setColor1("#0000FF"),
                new SingleKnobLightingConfig().setMode(SINGLE_KNOB_MODE.STATIC).setColor1("#FFFFFF"),
        };

        var data = unsigned(OutputInterpreter.buildRGBCustomData(100, knobs));

        assertArrayEquals(new int[] {
                2, 0,
                1, 0xFF, 0, 0,
                1, 0, 0xFF, 0,
                1, 0, 0, 0xFF,
                1, 0xFF, 0xFF, 0xFF,
                0, 0, 0, 0,
        }, data);
    }

    @Test
    void offUnsetAndGradientKnobs() {
        var knobs = new SingleKnobLightingConfig[] {
                new SingleKnobLightingConfig().setMode(SINGLE_KNOB_MODE.NONE).setColor1("#FF0000"),
                null,
                new SingleKnobLightingConfig().setMode(SINGLE_KNOB_MODE.VOLUME_GRADIENT).setColor1("#FF0000").setColor2("#0000FF"),
                new SingleKnobLightingConfig().setMode(SINGLE_KNOB_MODE.STATIC).setColor1("#000000"),
        };

        var data = unsigned(OutputInterpreter.buildRGBCustomData(100, knobs));

        assertEquals(22, data.length);
        assertArrayEquals(new int[] { 1, 0, 0, 0 }, Arrays.copyOfRange(data, 2, 6), "NONE is off");
        assertArrayEquals(new int[] { 1, 0, 0, 0 }, Arrays.copyOfRange(data, 6, 10), "an unset knob is off");
        assertArrayEquals(new int[] { 1, 0, 0, 0xFF }, Arrays.copyOfRange(data, 10, 14), "gradient shows its end colour");
        assertArrayEquals(new int[] { 1, 0, 0, 0 }, Arrays.copyOfRange(data, 14, 18), "static black is off");
        assertArrayEquals(new int[] { 0, 0, 1, 0 }, Arrays.copyOfRange(data, 18, 22), "only the gradient knob tracks volume");
    }
}
