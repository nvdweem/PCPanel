package com.getpcpanel.mutecolor;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;

import com.getpcpanel.commands.Commands;
import com.getpcpanel.voicemeeter.command.CommandVoiceMeeterAdvanced;
import com.getpcpanel.voicemeeter.command.CommandVoiceMeeterBasic;
import com.getpcpanel.voicemeeter.VoiceMeeterMuteEvent;
import com.getpcpanel.voicemeeter.Voicemeeter.ButtonType;
import com.getpcpanel.voicemeeter.Voicemeeter.ControlType;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

/**
 * Mute state of a VoiceMeeter strip/bus. VoiceMeeter pushes mute changes as {@link VoiceMeeterMuteEvent};
 * this resolver caches the last-seen state per control and, after updating the cache, fires a
 * {@link MuteOverridesDirtyEvent} so the override colours recompute against fresh state. Supports a
 * control following its own basic/advanced VoiceMeeter command, and the legacy {@code VoiceMeeter: …}
 * named-target pattern.
 */
@ApplicationScoped
public class VoiceMeeterMuteResolver implements MuteStateResolver {
    /** Named-target form: {@code VoiceMeeter: (Input|Output) <idx1-based>, <ButtonType>}. */
    public static final Pattern VM_PATTERN = Pattern.compile("VoiceMeeter: (Input|Output) (\\d+), (.*)");
    private static final Pattern ADVANCED = Pattern.compile("^(Strip|Bus)\\[(\\d+)]", Pattern.CASE_INSENSITIVE);

    @Inject
    Event<MuteOverridesDirtyEvent> dirty;

    private final Map<VmKey, Boolean> cache = new ConcurrentHashMap<>();

    public void onMute(@Observes VoiceMeeterMuteEvent event) {
        cache.put(new VmKey(event.ct(), event.idx(), event.button()), event.state());
        dirty.fire(new MuteOverridesDirtyEvent());
    }

    @Override
    public Optional<Boolean> resolve(Commands command, String target) {
        if (FOLLOW.equals(target)) {
            var basic = command.getCommand(CommandVoiceMeeterBasic.class).orElse(null);
            if (basic != null) {
                return lookup(basic.getCt(), basic.getIndex(), ButtonType.MUTE);
            }
            var advanced = command.getCommand(CommandVoiceMeeterAdvanced.class).orElse(null);
            if (advanced != null) {
                var matcher = ADVANCED.matcher(StringUtils.defaultString(advanced.getFullParam()));
                if (matcher.find()) {
                    var ct = "bus".equalsIgnoreCase(matcher.group(1)) ? ControlType.BUS : ControlType.STRIP;
                    return lookup(ct, NumberUtils.toInt(matcher.group(2), 0), ButtonType.MUTE);
                }
            }
            return Optional.empty();
        }

        var matcher = VM_PATTERN.matcher(target);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        var ct = ControlType.fromDn(matcher.group(1));
        var button = ButtonType.fromName(matcher.group(3));
        if (ct == null || button == null) {
            return Optional.empty();
        }
        return lookup(ct, NumberUtils.toInt(matcher.group(2), 0) - 1, button);
    }

    private Optional<Boolean> lookup(ControlType ct, int idx, ButtonType button) {
        return Optional.ofNullable(cache.get(new VmKey(ct, idx, button)));
    }

    private record VmKey(ControlType ct, int idx, ButtonType button) {
    }
}
