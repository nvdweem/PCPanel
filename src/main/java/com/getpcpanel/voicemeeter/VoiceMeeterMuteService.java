package com.getpcpanel.voicemeeter;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import com.getpcpanel.voicemeeter.Voicemeeter.ControlType;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class VoiceMeeterMuteService {
    private final Voicemeeter voiceMeeter;
    private final ApplicationEventPublisher eventPublisher;
    private final Map<ControlType, Map<Integer, Boolean>> muteMap = new EnumMap<>(ControlType.class);

    @EventListener(VoiceMeeterDirtyEvent.class)
    public void updateMuteState() {
        updateMuteStateFor(ControlType.STRIP);
        updateMuteStateFor(ControlType.BUS);
    }

    private void updateMuteStateFor(ControlType type) {
        for (var idx = 0; idx < voiceMeeter.getNum(type); idx++) {
            var target = muteMap.computeIfAbsent(type, ignored -> new HashMap<>());
            var current = target.get(idx);
            var newState = voiceMeeter.getMuteState(type, idx);

            if (!Objects.equals(current, newState)) {
                target.put(idx, newState);
                eventPublisher.publishEvent(new VoiceMeeterMuteEvent(type, idx, newState));
            }
        }
    }
}
