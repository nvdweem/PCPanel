package com.getpcpanel.mutecolor;

import java.util.Optional;

import org.apache.commons.lang3.StringUtils;

import com.getpcpanel.commands.Commands;
import com.getpcpanel.commands.command.CommandObsSetSourceVolume;
import com.getpcpanel.obs.OBS;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/** Mute state of an OBS source volume control ({@link CommandObsSetSourceVolume}). */
@ApplicationScoped
public class ObsMuteResolver implements MuteStateResolver {
    @Inject
    OBS obs;

    @Override
    public Optional<Boolean> resolve(Commands command, String target) {
        if (!FOLLOW.equals(target)) {
            return Optional.empty();
        }
        var cmd = command.getCommand(CommandObsSetSourceVolume.class).orElse(null);
        if (cmd == null || !obs.isConnected()) {
            return Optional.empty();
        }
        var sourceName = cmd.getSourceName();
        for (var entry : obs.getSourcesWithMuteState().entrySet()) {
            if (StringUtils.containsIgnoreCase(sourceName, entry.getKey()) || StringUtils.equalsIgnoreCase(sourceName, entry.getKey())) {
                return Optional.of(entry.getValue());
            }
        }
        return Optional.empty();
    }
}
