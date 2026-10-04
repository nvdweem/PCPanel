package com.getpcpanel.integration.device;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import org.junit.jupiter.api.Test;

import com.getpcpanel.commands.Commands;
import com.getpcpanel.commands.CommandsType;
import com.getpcpanel.commands.DialValueCalculator;
import com.getpcpanel.commands.curve.Curve;
import com.getpcpanel.commands.curve.CurveService;
import com.getpcpanel.device.Device;
import com.getpcpanel.device.DeviceHolder;
import com.getpcpanel.profile.DeviceSave;
import com.getpcpanel.profile.Save;
import com.getpcpanel.profile.SaveService;
import com.getpcpanel.integration.device.command.CommandBrightness;
import com.getpcpanel.device.provider.pcpanel.DeviceType;
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
        assertTrue(best.shaped());
    }

    @Test
    void anyNamedCurveWinsOverLinearNotJustTheBuiltInLogarithmic() {
        var linearLow = brightnessAt("a", 1, false);
        var custom = brightnessAt("b", 6, false);
        custom.getKnobSettings(6).setCurve("my-taper");

        var best = BrightnessService.bestBrightnessControl(List.of(linearLow, custom)).orElseThrow();
        assertEquals(6, best.index(), "a control with a shaped curve is preferred");
        assertTrue(best.shaped());
    }

    @Test
    void amongEqualCurvesTheLowestIndexWins() {
        var high = brightnessAt("a", 7, false);
        var low = brightnessAt("b", 3, false);
        assertEquals(3, BrightnessService.bestBrightnessControl(List.of(high, low)).orElseThrow().index());
    }

    @Test
    void buttonBrightnessWinsOverTheDialUntilCleared() {
        var sut = serviceWithBrightnessDialAt(255);
        assertEquals(OptionalInt.of(100), sut.runtimeBrightness("s"));

        sut.setButtonBrightness("s", 20);
        assertEquals(OptionalInt.of(20), sut.runtimeBrightness("s"));
        assertEquals(OptionalInt.of(100), sut.runtimeBrightness("other"), "only the pressed device changes");

        sut.clearButtonBrightness("s");
        assertEquals(OptionalInt.of(100), sut.runtimeBrightness("s"));
    }

    @Test
    void buttonBrightnessAppliesWithoutABrightnessDial() {
        var sut = new BrightnessService();
        assertTrue(sut.runtimeBrightness("s").isEmpty());
        sut.setButtonBrightness("s", 0);
        assertEquals(OptionalInt.of(0), sut.runtimeBrightness("s"));
    }

    private static BrightnessService serviceWithBrightnessDialAt(int raw) {
        var save = new Save();
        for (var serial : List.of("s", "other")) {
            var deviceSave = new DeviceSave();
            deviceSave.setProfiles(List.of(brightnessAt("p", 2, false)));
            save.getDevices().put(serial, deviceSave);
        }
        var device = mock(Device.class);
        when(device.hasKnobRotation(2)).thenReturn(true);
        when(device.getKnobRotation(2)).thenReturn(raw);
        var sut = new BrightnessService();
        sut.saveService = mock(SaveService.class);
        when(sut.saveService.get()).thenReturn(save);
        sut.devices = mock(DeviceHolder.class);
        when(sut.devices.getDevice(any())).thenReturn(Optional.of(device));
        sut.curves = mock(CurveService.class);
        when(sut.curves.calculatorFor(any())).thenReturn(new DialValueCalculator(null, Curve.LINEAR));
        return sut;
    }
}
