package com.getpcpanel.voicemeeter;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import com.getpcpanel.ui.LightningChangedToDefaultEvent;
import com.getpcpanel.voicemeeter.Voicemeeter.ButtonType;
import com.getpcpanel.voicemeeter.Voicemeeter.ControlType;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class VoiceMeeterMuteService {
    private final Voicemeeter voiceMeeter;
    private final ApplicationEventPublisher eventPublisher;
    private final Map<ControlType, Map<Integer, Map<ButtonType, Boolean>>> toggleMap = new EnumMap<>(ControlType.class);

    @EventListener(LightningChangedToDefaultEvent.class)
    public void resetMuteStates() {
        toggleMap.clear();
        if (voiceMeeter.login()) {
            updateMuteState();
        }
    }

    @EventListener(VoiceMeeterDirtyEvent.class)
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
                    eventPublisher.publishEvent(new VoiceMeeterMuteEvent(type, idx, button, newState));
                }
            }
        }
    }
}
