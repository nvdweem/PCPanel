package com.getpcpanel.hid;

import static com.getpcpanel.commands.Commands.hasCommands;
import static java.util.Objects.requireNonNullElse;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.context.ApplicationScoped;

import com.getpcpanel.commands.Commands;
import com.getpcpanel.commands.PCPanelControlEvent;
import com.getpcpanel.profile.BaseLayerService;
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
    BaseLayerService baseLayer;
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
            device.setKnobRotation(event.knob(), value);
            var settings = save.getProfile(event.serialNum()).map(p -> p.getKnobSettings(event.knob())).orElse(null);
            doDialAction(event.serialNum(), event.initial(), event.knob(), new DialValue(settings, value));
        });
    }

        public void onButtonPress(@Observes DeviceCommunicationHandler.ButtonPressEvent event) throws IOException {
        devices.getDevice(event.serialNum()).ifPresent(device -> device.setButtonPressed(event.button(), event.pressed()));
        if (event.pressed()) {
            doClickAction(event.serialNum(), event.button());
        } else {
            doReleaseAction(event.serialNum(), event.button());
        }
    }

    /**
     * Fires the button's release commands on button-up (push-to-talk). Release has no double-click
     * notion, so it dispatches directly rather than through the click/debounce path.
     */
    private void doReleaseAction(String serialNum, int button) {
        save.getProfile(serialNum)
            .map(p -> baseLayer.effectiveReleaseButton(serialNum, p, button))
            .filter(data -> hasCommands(data))
            .ifPresent(data -> eventBus.fire(new PCPanelControlEvent(serialNum, button, data, false, null)));
    }

    private void doDialAction(String serialNum, boolean initial, int knob, DialValue v) {
        save.getProfile(serialNum)
            .map(p -> baseLayer.effectiveDial(serialNum, p, knob))
            .filter(Commands::hasCommands)
            .ifPresent(data -> eventBus.fire(new PCPanelControlEvent(serialNum, knob, data, initial, v)));
    }

    void doClickAction(String serialNum, int button) {
        // Double-click detection only matters when the button actually has a double-click action bound. For a plain
        // button (the common case - e.g. a mute/toggle) the debounce only added latency and, worse, a second press
        // that landed within the interval was reclassified as a double-click and dropped, leaving toggles stuck on
        // their first state (#72). Fire those immediately so every press toggles reliably.
        if (!hasDblClickAction(serialNum, button)) {
            eventBus.fire(new ButtonClickEvent(serialNum, button, false));
            return;
        }
        var clickId = new ClickId(serialNum, button);
        var timeDiff = System.currentTimeMillis() - lastClicks.getOrDefault(clickId, 0L);
        determineClick(clickId, timeDiff);
    }

    private boolean hasDblClickAction(String serialNum, int button) {
        return save.getProfile(serialNum).map(p -> baseLayer.effectiveDblButton(serialNum, p, button)).filter(d -> hasCommands(d)).isPresent();
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
            var click = baseLayer.effectiveButton(event.serialNum(), profile, event.button());
            var dblClick = baseLayer.effectiveDblButton(event.serialNum(), profile, event.button());

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
