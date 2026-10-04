package com.getpcpanel.integration.device.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.getpcpanel.commands.Commands;
import com.getpcpanel.commands.CommandsType;
import com.getpcpanel.commands.DialValue;
import com.getpcpanel.commands.command.DeviceAction.DeviceActionParameters;
import com.getpcpanel.commands.command.DialAction.DialActionParameters;
import com.getpcpanel.commands.command.DialAction.DialCommandParams;
import com.getpcpanel.commands.curve.Curve;
import com.getpcpanel.device.DeviceHolder;
import com.getpcpanel.integration.device.BrightnessService;
import com.getpcpanel.integration.dialvalue.command.CommandSetDialValue;
import com.getpcpanel.integration.testutil.FakeCdi;

/** Brightness set from a button ("Run dial actions at a level") against the brightness dial, through the real command paths. */
class CommandBrightnessTest {
    private BrightnessService brightness;

    @BeforeEach
    void setUp() {
        brightness = new BrightnessService();
        var devices = mock(DeviceHolder.class);
        when(devices.getDevice(any())).thenReturn(Optional.empty());
        FakeCdi.register(BrightnessService.class, brightness);
        FakeCdi.register(DeviceHolder.class, devices);
    }

    @AfterEach
    void tearDown() {
        FakeCdi.clear();
    }

    @Test
    void movingTheDialTakesOverFromTheButton() {
        brightness.setButtonBrightness("s", 20);
        new CommandBrightness(null).execute(new DialActionParameters("s", false, new DialValue(null, Curve.LINEAR, 100)));
        assertEquals(OptionalInt.empty(), brightness.runtimeBrightness("s"));
    }

    @Test
    void theStartupSyncLeavesTheButtonBrightness() {
        brightness.setButtonBrightness("s", 20);
        new CommandBrightness(null).execute(new DialActionParameters("s", true, new DialValue(null, Curve.LINEAR, 100)));
        assertEquals(OptionalInt.of(20), brightness.runtimeBrightness("s"));
    }

    @Test
    void setDialValueTogglesBrightnessBackThroughMoveStartAndEnd() {
        var dial = new CommandBrightness(new DialCommandParams(false, 10, 10));
        var c = new CommandSetDialValue(30, new Commands(List.of(dial), CommandsType.allAtOnce), true);

        c.execute(new DeviceActionParameters("s"));
        assertEquals(OptionalInt.of(25), brightness.runtimeBrightness("s"));
        c.execute(new DeviceActionParameters("s"));
        assertEquals(OptionalInt.of(100), brightness.runtimeBrightness("s"), "back to the saved brightness it started at");
    }

    @Test
    void setDialValueTogglesBrightnessBackFromZeroInsideTheMoveStartDeadZone() {
        var dial = new CommandBrightness(new DialCommandParams(false, 10, null));
        var c = new CommandSetDialValue(0, new Commands(List.of(dial), CommandsType.allAtOnce), true);
        brightness.setButtonBrightness("s", 60);

        c.execute(new DeviceActionParameters("s"));
        assertEquals(OptionalInt.of(0), brightness.runtimeBrightness("s"));
        c.execute(new DeviceActionParameters("s"));
        assertEquals(OptionalInt.of(60), brightness.runtimeBrightness("s"));
    }
}
