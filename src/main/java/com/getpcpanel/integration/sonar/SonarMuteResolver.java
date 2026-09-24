package com.getpcpanel.integration.sonar;

import java.util.Optional;

import com.getpcpanel.commands.Commands;
import com.getpcpanel.integration.sonar.command.CommandSonar;
import com.getpcpanel.integration.volume.mutecolor.MuteStateResolver;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Mute state of the Sonar channel a control acts on. Both the volume dial and the mute button carry the
 * same {@link CommandSonar} target, so either drives the mute-override colour. Reads the cached snapshot
 * only — this runs on LED updates and must never block on HTTP, and never mutates {@link SonarService}'s
 * poll gate: that gate is derived from the save file (see {@link SonarService#onSaveChanged}), not from
 * which controls happen to be asked about.
 */
@ApplicationScoped
public class SonarMuteResolver implements MuteStateResolver {
    private final SonarService sonar;

    @Inject
    public SonarMuteResolver(SonarService sonar) {
        this.sonar = sonar;
    }

    @Override
    public Optional<Boolean> resolve(Commands command, String target) {
        if (!FOLLOW.equals(target)) {
            return Optional.empty();
        }
        var cmd = command.getCommand(CommandSonar.class).orElse(null);
        if (cmd == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(sonar.mutedOrNull(cmd.getChannel(), cmd.getMix()));
    }
}
