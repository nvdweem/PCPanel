package com.getpcpanel.hid;

import java.io.IOException;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import com.getpcpanel.commands.PCPanelControlEvent;
import com.getpcpanel.device.DeviceType;
import com.getpcpanel.profile.SaveService;
import com.getpcpanel.util.Util;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Service
@RequiredArgsConstructor
public final class InputInterpreter {
    private final SaveService save;
    private final DeviceHolder devices;
    private final ApplicationEventPublisher eventPublisher;

    public void onKnobRotate(String serialNum, int knob, int value) {
        var device = devices.getDevice(serialNum);
        if (device == null)
            return;
        if (device.getDeviceType() != DeviceType.PCPANEL_RGB)
            value = Util.map(value, 0, 255, 0, 100);
        device.setKnobRotation(knob, value);
        var settings = save.get().getDeviceSave(serialNum).getKnobSettings(knob);
        if (settings != null) {
            if (settings.isLogarithmic())
                value = log(value);
            value = Util.map(value, 0, 100, settings.getMinTrim(), settings.getMaxTrim());
        }
        doDialAction(serialNum, knob, value);
    }

    public void onButtonPress(String serialNum, int knob, boolean pressed) throws IOException {
        devices.getDevice(serialNum).setButtonPressed(knob, pressed);
        if (pressed)
            doClickAction(serialNum, knob);
    }

    private void doDialAction(String serialNum, int knob, int v) {
        var data = save.get().getDeviceSave(serialNum).getDialData(knob);
        if (data == null)
            return;
        eventPublisher.publishEvent(new PCPanelControlEvent(serialNum, knob, data.toRunnable(serialNum, v)));
    }

    private void doClickAction(String serialNum, int knob) {
        var data = save.get().getDeviceSave(serialNum).getButtonData(knob);
        if (data == null)
            return;
        eventPublisher.publishEvent(new PCPanelControlEvent(serialNum, knob, data.toRunnable(serialNum, null)));
    }

    @SuppressWarnings("NumericCastThatLosesPrecision")
    private static int log(int x) {
        var cons = 21.6679065336D;
        var ans = Math.pow(Math.E, x / cons) - 1.0D;
        return (int) Math.round(ans);
    }
}
