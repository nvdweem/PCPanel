package com.getpcpanel.integration.voicemeeter;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import com.getpcpanel.profile.LightingChangedToDefaultEvent;
import com.getpcpanel.integration.voicemeeter.Voicemeeter.ButtonType;
import com.getpcpanel.integration.voicemeeter.Voicemeeter.ControlType;

@ApplicationScoped
class VoiceMeeterMuteService {
    @Inject
    Voicemeeter voiceMeeter;
    @Inject
    Event<Object> eventBus;
    private final Map<ControlType, Map<Integer, Map<ButtonType, Boolean>>> toggleMap = new EnumMap<>(ControlType.class);

    public void resetMuteStates(@Observes LightingChangedToDefaultEvent event) {
        toggleMap.clear();
        if (voiceMeeter.login()) {
            updateMuteState();
        }
    }

    public void onVoiceMeeterDirty(@Observes VoiceMeeterDirtyEvent event) {
        updateMuteState();
    }

    private void updateMuteState() {
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
