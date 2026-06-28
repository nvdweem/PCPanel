package com.getpcpanel.analogbands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.getpcpanel.commands.Commands;
import com.getpcpanel.commands.CommandsType;
import com.getpcpanel.integration.analogbands.command.AnalogBand;
import com.getpcpanel.integration.analogbands.command.CommandAnalogBands;
import com.getpcpanel.profile.dto.LightingConfig;
import com.getpcpanel.profile.dto.LightingConfig.LightingMode;

/**
 * Verifies that the selected band's feedback colour is mapped to the right control override — a knob
 * override for analog indices below the knob count, a slider + slider-label override above it — and
 * that a position with no colour clears any previous override.
 */
class AnalogBandColorServiceTest {
    /** Raw 0-255 reading that lands at the given percentage of travel. */
    private static int raw(double pct) {
        return (int) Math.round(pct / 100.0 * 255.0);
    }

    /** A stepped switch resting on a band of the given colour (or no colour when null). */
    private static Commands steppedSwitchOn(String color) {
        var cmd = new CommandAnalogBands(List.of(new AnalogBand(0, 100, color, Commands.EMPTY)));
        cmd.advance(raw(50), true); // select the only band
        return new Commands(List.of(cmd), CommandsType.allAtOnce);
    }

    private static LightingConfig proCustom() {
        var lc = new LightingConfig(5, 4); // Pro: knobs at analog index 0-4, sliders at 5-8
        lc.setLightingMode(LightingMode.CUSTOM);
        return lc;
    }

    @Test
    void knobPositionGetsStaticOverrideOfTheBandColor() {
        var service = new AnalogBandColorService();
        var changed = service.applyOverrides("serial", proCustom(), Map.of(2, steppedSwitchOn("#00FF00")));

        assertTrue(changed);
        var override = service.getOverrideColorProvider().getDialOverride("serial", 2);
        assertTrue(override.isPresent(), "knob 2 should show its band colour");
        assertEquals("#00FF00", override.get().getColor1());
    }

    @Test
    void sliderPositionLightsBothSliderAndLabel() {
        var service = new AnalogBandColorService();
        // analog index 5 == slider 0 on a Pro (5 knobs).
        var changed = service.applyOverrides("serial", proCustom(), Map.of(5, steppedSwitchOn("#0000FF")));

        assertTrue(changed);
        var provider = service.getOverrideColorProvider();
        assertEquals("#0000FF", provider.getSliderOverride("serial", 0).orElseThrow().getColor1());
        assertEquals("#0000FF", provider.getSliderLabelOverride("serial", 0).orElseThrow().getColor());
    }

    @Test
    void blackIsAUsableBandColourNotTreatedAsOff() {
        var service = new AnalogBandColorService();
        var changed = service.applyOverrides("serial", proCustom(), Map.of(2, steppedSwitchOn("#000000")));

        assertTrue(changed, "black is an explicit feedback colour, not the off state");
        assertEquals("#000000", service.getOverrideColorProvider().getDialOverride("serial", 2).orElseThrow().getColor1());
    }

    @Test
    void positionWithoutColourAppliesNoOverride() {
        var service = new AnalogBandColorService();
        var changed = service.applyOverrides("serial", proCustom(), Map.of(2, steppedSwitchOn(null)));

        assertFalse(changed);
        assertTrue(service.getOverrideColorProvider().getDialOverride("serial", 2).isEmpty());
    }

    @Test
    void movingToAColourlessPositionClearsThePreviousOverride() {
        var service = new AnalogBandColorService();
        var lc = proCustom();

        service.applyOverrides("serial", lc, Map.of(2, steppedSwitchOn("#00FF00")));
        assertTrue(service.getOverrideColorProvider().getDialOverride("serial", 2).isPresent());

        var changed = service.applyOverrides("serial", lc, Map.of(2, steppedSwitchOn(null)));
        assertTrue(changed, "losing the band colour should clear the override");
        assertTrue(service.getOverrideColorProvider().getDialOverride("serial", 2).isEmpty());
    }
}
