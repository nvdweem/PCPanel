package com.getpcpanel.hid;

import static com.getpcpanel.commands.Commands.hasCommands;
import static com.getpcpanel.util.Util.map;
import static java.util.Objects.requireNonNullElse;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nonnull;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import com.getpcpanel.commands.PCPanelControlEvent;
import com.getpcpanel.device.DeviceType;
import com.getpcpanel.profile.Profile;
import com.getpcpanel.profile.SaveService;
import com.getpcpanel.util.Debouncer;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Service
@RequiredArgsConstructor
public final class InputInterpreter {
    private final SaveService save;
    private final DeviceHolder devices;
    private final ApplicationEventPublisher eventPublisher;
    private final Debouncer debouncer;
    private final Map<ClickId, Long> lastClicks = new HashMap<>();

    @EventListener
    public void onKnobRotate(DeviceCommunicationHandler.KnobRotateEvent event) {
        devices.getDevice(event.serialNum()).ifPresent(device -> {
            var value = event.value();
            if (device.getDeviceType() == DeviceType.PCPANEL_RGB) {
                value = map(value, 0, 100, 0, 255);
            }
            device.setKnobRotation(event.knob(), value);
            var settings = save.getProfile(event.serialNum()).map(p -> p.getKnobSettings(event.knob())).orElse(null);
            doDialAction(event.serialNum(), event.initial(), event.knob(), new DialValueCalculator(settings, value));
        });
    }

    @EventListener
    public void onButtonPress(DeviceCommunicationHandler.ButtonPressEvent event) throws IOException {
        devices.getDevice(event.serialNum()).ifPresent(device -> device.setButtonPressed(event.button(), event.pressed()));
        if (event.pressed())
            doClickAction(event.serialNum(), event.button());
    }

    private void doDialAction(String serialNum, boolean initial, int knob, DialValueCalculator v) {
        save.getProfile(serialNum).map(p -> p.getDialData(knob)).ifPresent(data -> eventPublisher.publishEvent(new PCPanelControlEvent(serialNum, knob, data, initial, v)));
    }

    private void doClickAction(String serialNum, int knob) {
        save.getProfile(serialNum).ifPresent(profile -> {
            var clickId = new ClickId(serialNum, knob);
            var timeDiff = System.currentTimeMillis() - lastClicks.getOrDefault(clickId, 0L);
            determineClick(profile, clickId, timeDiff);
        });
    }

    private void determineClick(@Nonnull Profile profile, ClickId clickId, long timeDiff) {
        var debounceTime = requireNonNullElse(save.get().getDblClickInterval(), 500L);
        var click = profile.getButtonData(clickId.button());
        var dblClick = profile.getDblButtonData(clickId.button());
        var hasDblClick = hasCommands(dblClick);
        var shouldDblClick = hasDblClick && timeDiff < debounceTime;

        if (shouldDblClick) {
            debouncer.prevent(clickId);
            eventPublisher.publishEvent(new PCPanelControlEvent(clickId.serialNum(), clickId.button(), dblClick, false, null));
            lastClicks.remove(clickId);
            return;
        }

        lastClicks.put(clickId, System.currentTimeMillis());
        if (hasCommands(click)) {
            var event = new PCPanelControlEvent(clickId.serialNum(), clickId.button(), click, false, null);
            if (hasDblClick && save.get().isPreventClickWhenDblClick()) {
                debouncer.debounce(clickId, () -> eventPublisher.publishEvent(event), debounceTime, TimeUnit.MILLISECONDS);
            } else {
                eventPublisher.publishEvent(event);
            }
        }
    }

    private record ClickId(String serialNum, int button) {
    }
}
