package com.getpcpanel.hid;

import static com.getpcpanel.commands.Commands.hasCommands;
import static com.getpcpanel.util.Util.map;
import static java.util.Objects.requireNonNullElse;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.context.ApplicationScoped;

import com.getpcpanel.commands.PCPanelControlEvent;
import com.getpcpanel.device.DeviceType;
import com.getpcpanel.profile.SaveService;
import com.getpcpanel.util.Debouncer;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Log4j2
@ApplicationScoped
public final class InputInterpreter {
    @Inject
    SaveService save;
    @Inject
    DeviceHolder devices;
    @Inject
    Event<Object> eventBus;
    @Inject
    Debouncer debouncer;
    private final Map<ClickId, Long> lastClicks = new HashMap<>();

        public void onKnobRotate(@Observes DeviceCommunicationHandler.KnobRotateEvent event) {
        devices.getDevice(event.serialNum()).ifPresent(device -> {
            var value = event.value();
            if (device.deviceType() == DeviceType.PCPANEL_RGB) {
                value = map(value, 0, 100, 0, 255);
            }
            device.setKnobRotation(event.knob(), value);
            var settings = save.getProfile(event.serialNum()).map(p -> p.getKnobSettings(event.knob())).orElse(null);
            doDialAction(event.serialNum(), event.initial(), event.knob(), new DialValue(settings, value));
        });
    }

        public void onButtonPress(@Observes DeviceCommunicationHandler.ButtonPressEvent event) throws IOException {
        devices.getDevice(event.serialNum()).ifPresent(device -> device.setButtonPressed(event.button(), event.pressed()));
        if (event.pressed())
            doClickAction(event.serialNum(), event.button());
    }

    private void doDialAction(String serialNum, boolean initial, int knob, DialValue v) {
        save.getProfile(serialNum).map(p -> p.getDialData(knob)).ifPresent(data -> eventBus.fire(new PCPanelControlEvent(serialNum, knob, data, initial, v)));
    }

    private void doClickAction(String serialNum, int knob) {
        var clickId = new ClickId(serialNum, knob);
        var timeDiff = System.currentTimeMillis() - lastClicks.getOrDefault(clickId, 0L);
        determineClick(clickId, timeDiff);
    }

    private void determineClick(ClickId clickId, long timeDiff) {
        long debounceTime = requireNonNullElse(save.get().getDblClickInterval(), 500L);
        var isDblClick = timeDiff < debounceTime;

        if (isDblClick) {
            debouncer.debounce(clickId, () -> {
            }, debounceTime, TimeUnit.MILLISECONDS);
            eventBus.fire(new ButtonClickEvent(clickId.serialNum(), clickId.button(), true));
            lastClicks.remove(clickId);
            return;
        }

        lastClicks.put(clickId, System.currentTimeMillis());
        Runnable trigger = () -> eventBus.fire(new ButtonClickEvent(clickId.serialNum(), clickId.button(), false));
        if (save.get().isPreventClickWhenDblClick()) {
            debouncer.debounce(clickId, trigger, debounceTime, TimeUnit.MILLISECONDS);
        } else {
            trigger.run();
        }
    }

        public void onButtonPress(@Observes ButtonClickEvent event) {
        save.getProfile(event.serialNum()).ifPresent(profile -> {
            var click = profile.getButtonData(event.button());
            var dblClick = profile.getDblButtonData(event.button());

            if (event.dblClick() && hasCommands(dblClick)) {
                eventBus.fire(new PCPanelControlEvent(event.serialNum(), event.button(), dblClick, false, null));
            } else if (!event.dblClick() && hasCommands(click)) {
                eventBus.fire(new PCPanelControlEvent(event.serialNum(), event.button(), click, false, null));
            }
        });
    }

    private record ClickId(String serialNum, int button) {
    }
}
