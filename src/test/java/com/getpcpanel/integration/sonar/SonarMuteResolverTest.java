package com.getpcpanel.integration.sonar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.getpcpanel.commands.Commands;
import com.getpcpanel.integration.sonar.command.CommandSonarVolume;
import com.getpcpanel.integration.volume.mutecolor.MuteStateResolver;

import org.junit.jupiter.api.Test;

class SonarMuteResolverTest {
    private SonarService serviceWith(SonarLevel level) {
        var client = new SonarClient(Path.of("no-such-coreProps.json")) {
            @Override public Optional<SonarMode> fetchMode() {
                return Optional.of(SonarMode.stream);
            }
        };
        var service = SonarServiceFixtures.service(client, true);
        service.replaceState(new SonarState(SonarMode.stream, Map.of(
                SonarRoute.of(SonarMode.stream, SonarMix.monitoring, SonarChannel.Game), level)));
        return service;
    }

    private Commands controlWithSonarDial() {
        return new Commands(List.of(
                new CommandSonarVolume(SonarChannel.Game, SonarMixSelection.monitoring, false, null)), null);
    }

    @Test
    void followsTheMuteStateOfTheControlsOwnSonarCommand() {
        var resolver = new SonarMuteResolver(serviceWith(new SonarLevel(0.5, true)));

        assertEquals(Optional.of(true), resolver.resolve(controlWithSonarDial(), MuteStateResolver.FOLLOW));
    }

    @Test
    void zeroVolumeIsNotMute() {
        // Verified against Sonar: volume and mute are independent.
        var resolver = new SonarMuteResolver(serviceWith(new SonarLevel(0.0, false)));

        assertEquals(Optional.of(false), resolver.resolve(controlWithSonarDial(), MuteStateResolver.FOLLOW));
    }

    @Test
    void ignoresControlsWithNoSonarCommand() {
        var resolver = new SonarMuteResolver(serviceWith(new SonarLevel(0.5, true)));

        assertTrue(resolver.resolve(new Commands(List.of(), null), MuteStateResolver.FOLLOW).isEmpty());
    }

    @Test
    void ignoresANonFollowTarget() {
        var resolver = new SonarMuteResolver(serviceWith(new SonarLevel(0.5, true)));

        assertTrue(resolver.resolve(controlWithSonarDial(), "Speakers").isEmpty());
    }
}
