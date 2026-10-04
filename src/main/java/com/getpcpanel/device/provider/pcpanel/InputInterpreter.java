package com.getpcpanel.device.provider.pcpanel;

import com.getpcpanel.device.DeviceHolder;
import com.getpcpanel.commands.DialValue;

import static com.getpcpanel.commands.Commands.hasCommands;
import static java.util.Objects.requireNonNullElse;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.context.ApplicationScoped;

import com.getpcpanel.commands.Commands;
import com.getpcpanel.commands.PCPanelControlEvent;
import com.getpcpanel.commands.curve.CurveService;
import com.getpcpanel.profile.BaseLayerService;
import com.getpcpanel.profile.SaveService;
import com.getpcpanel.util.concurrent.Debouncer;

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
    @Inject
    CurveService curves;
    private final Map<ClickId, Long> lastClicks = new HashMap<>();
    /** Buttons pressed while they had a hold action, whose press is not decided yet. */
    private final Set<ClickId> holdArmed = new HashSet<>();
    /** Buttons whose hold actions ran during the current press. */
    private final Set<ClickId> holdFired = new HashSet<>();

        public void onKnobRotate(@Observes DeviceCommunicationHandler.KnobRotateEvent event) {
        devices.getDevice(event.serialNum()).ifPresent(device -> {
            var value = event.value();
            device.setKnobRotation(event.knob(), value);
            var settings = save.getProfile(event.serialNum()).map(p -> baseLayer.effectiveKnobSetting(event.serialNum(), p, event.knob())).orElse(null);
            doDialAction(event.serialNum(), event.initial(), event.knob(), new DialValue(settings, curves.forControl(settings), value));
        });
    }

        public void onButtonPress(@Observes DeviceCommunicationHandler.ButtonPressEvent event) throws IOException {
        devices.getDevice(event.serialNum()).ifPresent(device -> device.setButtonPressed(event.button(), event.pressed()));
        if (event.pressed()) {
            doPress(event.serialNum(), event.button());
        } else {
            doRelease(event.serialNum(), event.button());
        }
    }

    /**
     * A button with hold actions is decided when it comes up or when it has been down for {@code holdInterval},
     * whichever is first: held that long it runs its hold actions, released sooner it is an ordinary press.
     * A button without hold actions is a press the moment it goes down.
     */
    void doPress(String serialNum, int button) {
        if (!hasHoldAction(serialNum, button)) {
            doClickAction(serialNum, button);
            return;
        }
        var id = new ClickId(serialNum, button);
        synchronized (this) {
            holdArmed.add(id);
            holdFired.remove(id);
        }
        debouncer.debounce(new HoldKey(id), () -> onHeld(id), save.get().getHoldInterval(), TimeUnit.MILLISECONDS);
    }

    void doRelease(String serialNum, int button) {
        var id = new ClickId(serialNum, button);
        boolean wasArmed;
        boolean held;
        synchronized (this) {
            wasArmed = holdArmed.remove(id);
            held = holdFired.remove(id);
        }
        if (wasArmed) {
            debouncer.cancel(new HoldKey(id));
            if (!held) {
                doClickAction(serialNum, button);
            }
        }
        doReleaseAction(serialNum, button);
    }

    private void onHeld(ClickId id) {
        synchronized (this) {
            if (!holdArmed.remove(id)) {
                return; // released in the meantime
            }
            holdFired.add(id);
        }
        save.getProfile(id.serialNum())
            .map(p -> baseLayer.effectiveHoldButton(id.serialNum(), p, id.button()))
            .filter(data -> hasCommands(data))
            .ifPresent(data -> eventBus.fire(new PCPanelControlEvent(id.serialNum(), id.button(), data, false, null, PCPanelControlEvent.Source.HOLD)));
    }

    private boolean hasHoldAction(String serialNum, int button) {
        return save.getProfile(serialNum).map(p -> baseLayer.effectiveHoldButton(serialNum, p, button)).filter(d -> hasCommands(d)).isPresent();
    }

    /**
     * Fires the button's release commands on button-up (push-to-talk). Release has no double-click
     * notion, so it dispatches directly rather than through the click/debounce path.
     */
    private void doReleaseAction(String serialNum, int button) {
        save.getProfile(serialNum)
            .map(p -> baseLayer.effectiveReleaseButton(serialNum, p, button))
            .filter(data -> hasCommands(data))
            .ifPresent(data -> eventBus.fire(new PCPanelControlEvent(serialNum, button, data, false, null, PCPanelControlEvent.Source.RELEASE)));
    }

    private void doDialAction(String serialNum, boolean initial, int knob, DialValue v) {
        save.getProfile(serialNum)
            .map(p -> baseLayer.effectiveDial(serialNum, p, knob))
            .filter(Commands::hasCommands)
            .ifPresent(data -> eventBus.fire(new PCPanelControlEvent(serialNum, knob, data, initial, v, PCPanelControlEvent.Source.DIAL)));
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
                eventBus.fire(new PCPanelControlEvent(event.serialNum(), event.button(), dblClick, false, null, PCPanelControlEvent.Source.PRESS));
            } else if (!event.dblClick() && hasCommands(click)) {
                eventBus.fire(new PCPanelControlEvent(event.serialNum(), event.button(), click, false, null, PCPanelControlEvent.Source.PRESS));
            }
        });
    }

    private record ClickId(String serialNum, int button) {
    }

    private record HoldKey(ClickId id) {
    }
}
