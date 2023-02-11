package com.getpcpanel.hid;

import static com.getpcpanel.util.Util.map;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nonnull;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import com.getpcpanel.commands.PCPanelControlEvent;
import com.getpcpanel.commands.command.CommandNoOp;
import com.getpcpanel.device.DeviceType;
import com.getpcpanel.profile.Profile;
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
    private final Map<ClickId, Long> lastClicks = new HashMap<>();

    @EventListener
    public void onKnobRotate(DeviceCommunicationHandler.KnobRotateEvent event) {
        devices.getDevice(event.serialNum()).ifPresent(device -> {
            var value = event.value();
            if (device.getDeviceType() != DeviceType.PCPANEL_RGB)
                value = map(value, 0, 255, 0, 100);
            device.setKnobRotation(event.knob(), value);
            var settings = save.getProfile(event.serialNum()).map(p -> p.getKnobSettings(event.knob())).orElse(null);
            if (settings != null) {
                if (settings.isLogarithmic())
                    value = log(value);
                value = map(value, 0, 100, settings.getMinTrim(), settings.getMaxTrim());
            }
            doDialAction(event.serialNum(), event.initial(), event.knob(), event.value(), value);
        });
    }

    @EventListener
    public void onButtonPress(DeviceCommunicationHandler.ButtonPressEvent event) throws IOException {
        devices.getDevice(event.serialNum()).ifPresent(device -> device.setButtonPressed(event.button(), event.pressed()));
        if (event.pressed())
            doClickAction(event.serialNum(), event.button());
    }

    private void doDialAction(String serialNum, boolean initial, int knob, int vRaw, int v) {
        save.getProfile(serialNum).map(p -> p.getDialData(knob)).ifPresent(data -> eventPublisher.publishEvent(new PCPanelControlEvent(serialNum, knob, data, initial, vRaw, v)));
    }

    private void doClickAction(String serialNum, int knob) {
        save.getProfile(serialNum).ifPresent(profile -> {
            var clickId = new ClickId(serialNum, knob);
            var lastClick = lastClicks.getOrDefault(clickId, 0L);
            var timeDiff = System.currentTimeMillis() - lastClick;

            determineClick(profile, serialNum, knob, timeDiff);
            lastClicks.put(clickId, System.currentTimeMillis());
        });
    }

    private void determineClick(@Nonnull Profile profile, String serialNum, int knob, long timeDiff) {
        var dblClick = profile.getDblButtonData(knob);
        var shouldDblClick = dblClick != null && timeDiff < 500;
        var data = shouldDblClick ? dblClick : profile.getButtonData(knob);

        //noinspection ObjectEquality
        if (data != null && data != CommandNoOp.NOOP) {
            eventPublisher.publishEvent(new PCPanelControlEvent(serialNum, knob, data, false, null, null));
        }
    }

    @SuppressWarnings("NumericCastThatLosesPrecision")
    private static int log(int x) {
        var cons = 21.6679065336D;
        var ans = Math.pow(Math.E, x / cons) - 1.0D;
        return (int) Math.round(ans);
    }

    private record ClickId(String serialNum, int button) {
    }
}
