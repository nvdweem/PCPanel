package com.getpcpanel.mutecolor;

import java.util.Optional;

import org.apache.commons.lang3.StringUtils;

import com.getpcpanel.commands.Commands;
import com.getpcpanel.integration.voicemeeter.VoiceMeeterMuteResolver;
import com.getpcpanel.cpp.ISndCtrl;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Catch-all for a mute override that watches a fixed audio device <em>by name</em> (i.e. the control's
 * configured target is a device name rather than {@link #FOLLOW}). Lowest priority so it only runs once
 * the integration-specific resolvers (incl. {@link VoiceMeeterMuteResolver}, which owns the
 * {@code VoiceMeeter: …} patterns) have declined.
 */
@Priority(-100)
@ApplicationScoped
public class NamedDeviceMuteResolver implements MuteStateResolver {
    @Inject
    ISndCtrl sndCtrl;

    @Override
    public Optional<Boolean> resolve(Commands command, String target) {
        if (FOLLOW.equals(target) || StringUtils.isBlank(target)) {
            return Optional.empty();
        }
        if (VoiceMeeterMuteResolver.VM_PATTERN.matcher(target).matches()) {
            return Optional.empty();
        }
        for (var device : sndCtrl.devices()) {
            if (StringUtils.containsIgnoreCase(device.name(), target)) {
                return Optional.of(device.muted());
            }
        }
        return Optional.empty();
    }
}
