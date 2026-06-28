package com.getpcpanel.integration.obs;

import com.getpcpanel.mutecolor.MuteStateResolver;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;

import com.getpcpanel.commands.Commands;
import com.getpcpanel.integration.obs.command.CommandObsMuteSource;
import com.getpcpanel.integration.obs.command.CommandObsSetSourceVolume;
import com.getpcpanel.integration.obs.OBS;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Mute state of an OBS source, followed by either an OBS source-volume dial
 * ({@link CommandObsSetSourceVolume}) or an OBS mute-source button ({@link CommandObsMuteSource}).
 */
@ApplicationScoped
public class ObsMuteResolver implements MuteStateResolver {
    @Inject
    OBS obs;

    @Override
    public Optional<Boolean> resolve(Commands command, String target) {
        // No isConnected() gate: getSourcesWithMuteState() is empty when OBS is down, so the loop below
        // resolves to empty anyway — and not gating lets the dev mute simulation drive this resolver.
        if (!FOLLOW.equals(target)) {
            return Optional.empty();
        }
        var sourceName = command.getCommand(CommandObsSetSourceVolume.class).map(CommandObsSetSourceVolume::getSourceName)
                .or(() -> command.getCommand(CommandObsMuteSource.class).map(CommandObsMuteSource::getSource))
                .orElse(null);
        if (sourceName == null) {
            return Optional.empty();
        }
        for (var entry : obs.getSourcesWithMuteState().entrySet()) {
            if (StringUtils.containsIgnoreCase(sourceName, entry.getKey()) || StringUtils.equalsIgnoreCase(sourceName, entry.getKey())) {
                return Optional.of(entry.getValue());
            }
        }
        return Optional.empty();
    }
}
