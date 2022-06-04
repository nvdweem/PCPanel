package com.getpcpanel.hid;

import java.io.IOException;

import com.getpcpanel.Main;
import com.getpcpanel.commands.CommandDispatcher;
import com.getpcpanel.device.DeviceType;
import com.getpcpanel.profile.Save;
import com.getpcpanel.util.Util;

import lombok.extern.log4j.Log4j2;

@Log4j2
public final class InputInterpreter {

    private InputInterpreter() {
    }

    public static void onKnobRotate(String serialNum, int knob, int value) {
        var device = Main.devices.get(serialNum);
        if (device == null)
            return;
        if (device.getDeviceType() != DeviceType.PCPANEL_RGB)
            value = Util.map(value, 0, 255, 0, 100);
        Main.devices.get(serialNum).setKnobRotation(knob, value);
        var settings = Save.getDeviceSave(serialNum).getKnobSettings(knob);
        if (settings != null) {
            if (settings.isLogarithmic())
                value = log(value);
            value = Util.map(value, 0, 100, settings.getMinTrim(), settings.getMaxTrim());
        }
        doDialAction(serialNum, knob, value);
    }

    public static void onButtonPress(String serialNum, int knob, boolean pressed) throws IOException {
        Main.devices.get(serialNum).setButtonPressed(knob, pressed);
        if (pressed)
            doClickAction(serialNum, knob);
    }

    private static void doDialAction(String serialNum, int knob, int v) {
        var data = Save.getDeviceSave(serialNum).getDialData(knob);
        if (data == null)
            return;
        CommandDispatcher.pushVolumeChange(serialNum, knob, data.toRunnable(serialNum, v));
    }

    private static void doClickAction(String serialNum, int knob) {
        var data = Save.getDeviceSave(serialNum).getButtonData(knob);
        if (data == null)
            return;

        CommandDispatcher.pushVolumeChange(serialNum, knob, data.toRunnable(serialNum, null));
    }

    @SuppressWarnings("NumericCastThatLosesPrecision")
    private static int log(int x) {
        var cons = 21.6679065336D;
        var ans = Math.pow(Math.E, x / cons) - 1.0D;
        return (int) Math.round(ans);
    }
}
