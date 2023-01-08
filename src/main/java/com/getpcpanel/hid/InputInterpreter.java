package com.getpcpanel.hid;

import static com.getpcpanel.util.Util.map;

import java.io.IOException;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import com.getpcpanel.commands.PCPanelControlEvent;
import com.getpcpanel.device.DeviceType;
import com.getpcpanel.profile.SaveService;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Service
@RequiredArgsConstructor
public final class InputInterpreter {
    private final SaveService save;
    private final DeviceHolder devices;
    private final ApplicationEventPublisher eventPublisher;

    @EventListener
    public void onKnobRotate(DeviceCommunicationHandler.KnobRotateEvent event) {
        var value = event.value();

        var device = devices.getDevice(event.serialNum());
        if (device == null)
            return;
        if (device.getDeviceType() != DeviceType.PCPANEL_RGB)
            value = map(value, 0, 255, 0, 100);
        device.setKnobRotation(event.knob(), value);
        var settings = save.getProfile(event.serialNum()).getKnobSettings(event.knob());
        if (settings != null) {
            if (settings.isLogarithmic())
                value = log(value);
            value = map(value, 0, 100, settings.getMinTrim(), settings.getMaxTrim());
        }
        doDialAction(event.serialNum(), event.initial(), event.knob(), value);
    }

    @EventListener
    public void onButtonPress(DeviceCommunicationHandler.ButtonPressEvent event) throws IOException {
        devices.getDevice(event.serialNum()).setButtonPressed(event.button(), event.pressed());
        if (event.pressed())
            doClickAction(event.serialNum(), event.button());
    }

    private void doDialAction(String serialNum, boolean initial, int knob, int v) {
        var data = save.getProfile(serialNum).getDialData(knob);
        if (data == null)
            return;
        eventPublisher.publishEvent(new PCPanelControlEvent(serialNum, knob, data.toRunnable(initial, serialNum, v)));
    }

    private void doClickAction(String serialNum, int knob) {
        var data = save.getProfile(serialNum).getButtonData(knob);
        if (data == null)
            return;
        eventPublisher.publishEvent(new PCPanelControlEvent(serialNum, knob, data.toRunnable(false, serialNum, null)));
    }

    @SuppressWarnings("NumericCastThatLosesPrecision")
    private static int log(int x) {
        var cons = 21.6679065336D;
        var ans = Math.pow(Math.E, x / cons) - 1.0D;
        return (int) Math.round(ans);
    }
}
