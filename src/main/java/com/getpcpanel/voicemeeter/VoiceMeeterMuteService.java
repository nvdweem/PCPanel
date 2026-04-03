package com.getpcpanel.voicemeeter;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.enterprise.context.ApplicationScoped;

import com.getpcpanel.profile.LightingChangedToDefaultEvent;
import com.getpcpanel.voicemeeter.Voicemeeter.ButtonType;
import com.getpcpanel.voicemeeter.Voicemeeter.ControlType;

import lombok.RequiredArgsConstructor;

@ApplicationScoped
public class VoiceMeeterMuteService {
    @Inject
    Voicemeeter voiceMeeter;
    @Inject
    Event<Object> eventBus;
    private final Map<ControlType, Map<Integer, Map<ButtonType, Boolean>>> toggleMap = new EnumMap<>(ControlType.class);

        public void resetMuteStates() {
        toggleMap.clear();
        if (voiceMeeter.login()) {
            updateMuteState();
        }
    }

        public void updateMuteState() {
        updateMuteStateFor(ControlType.STRIP);
        updateMuteStateFor(ControlType.BUS);
    }

    private void updateMuteStateFor(ControlType type) {
        var version = voiceMeeter.getVersion();
        if (version == null) {
            return;
        }

        for (var idx = 0; idx < voiceMeeter.getNum(type); idx++) {
            var target = toggleMap.computeIfAbsent(type, ignored -> new HashMap<>());
            var currentStates = target.computeIfAbsent(idx, ignored -> new EnumMap<>(ButtonType.class));

            for (var button : ButtonType.stateButtonsFor(type, version)) {
                var current = currentStates.get(button);
                var newState = voiceMeeter.getButtonState(type, idx, button);
                if (!Objects.equals(current, newState)) {
                    currentStates.put(button, newState);
                    eventBus.fire(new VoiceMeeterMuteEvent(type, idx, button, newState));
                }
            }
        }
    }
}
