package com.getpcpanel.integration.volume.mutecolor;

import java.util.Optional;

import org.apache.commons.lang3.StringUtils;

import com.getpcpanel.commands.Commands;
import com.getpcpanel.integration.voicemeeter.VoiceMeeterMuteResolver;
import com.getpcpanel.integration.volume.platform.AudioDevice;
import com.getpcpanel.integration.volume.platform.ISndCtrl;

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
class NamedDeviceMuteResolver implements MuteStateResolver {
    @Inject
    ISndCtrl sndCtrl;

    @Override
    public Optional<Boolean> resolve(Commands command, String target) {
        if (FOLLOW.equals(target) || StringUtils.isBlank(target)) {
            return Optional.empty();
        }
        if (target.startsWith(ProcessMuteResolver.APP_PREFIX) || VoiceMeeterMuteResolver.VM_PATTERN.matcher(target).matches()) {
            return Optional.empty();
        }
        // An exact name first: Wave Link names a virtual device after the mic it carries, so the mic's name is part of it.
        var devices = sndCtrl.devices();
        return devices.stream().filter(d -> StringUtils.equalsIgnoreCase(d.name(), target)).findFirst()
                      .or(() -> devices.stream().filter(d -> StringUtils.containsIgnoreCase(d.name(), target)).findFirst())
                      .map(AudioDevice::muted);
    }
}
