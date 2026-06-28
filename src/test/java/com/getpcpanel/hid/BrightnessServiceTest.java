package com.getpcpanel.hid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.getpcpanel.commands.Commands;
import com.getpcpanel.commands.CommandsType;
import com.getpcpanel.integration.device.command.CommandBrightness;
import com.getpcpanel.device.DeviceType;
import com.getpcpanel.profile.Profile;

class BrightnessServiceTest {
    private static Profile profile(String name) {
        return new Profile(name, DeviceType.PCPANEL_PRO);
    }

    private static Profile brightnessAt(String name, int index, boolean logarithmic) {
        var p = profile(name);
        p.setDialData(index, new Commands(List.of(new CommandBrightness(null)), CommandsType.allAtOnce));
        p.getKnobSettings(index).setLogarithmic(logarithmic);
        return p;
    }

    @Test
    void noBrightnessControlAnywhereYieldsNothing() {
        assertTrue(BrightnessService.bestBrightnessControl(List.of(profile("a"), profile("b"))).isEmpty());
    }

    @Test
    void findsBrightnessEvenInANonActiveProfile() {
        var main = profile("main");
        var other = brightnessAt("other", 4, false);
        var best = BrightnessService.bestBrightnessControl(List.of(main, other)).orElseThrow();
        assertEquals(4, best.index());
    }

    @Test
    void logarithmicWinsOverLinearRegardlessOfIndex() {
        var linearLow = brightnessAt("a", 1, false);   // lower index, but linear
        var logHigh = brightnessAt("b", 6, true);       // higher index, but logarithmic
        var best = BrightnessService.bestBrightnessControl(List.of(linearLow, logHigh)).orElseThrow();
        assertEquals(6, best.index(), "a logarithmic brightness control is preferred");
        assertTrue(best.logarithmic());
    }

    @Test
    void amongEqualCurvesTheLowestIndexWins() {
        var high = brightnessAt("a", 7, false);
        var low = brightnessAt("b", 3, false);
        assertEquals(3, BrightnessService.bestBrightnessControl(List.of(high, low)).orElseThrow().index());
    }
}
