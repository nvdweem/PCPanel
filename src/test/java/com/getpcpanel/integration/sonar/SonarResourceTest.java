package com.getpcpanel.integration.sonar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import com.getpcpanel.integration.sonar.rest.SonarResource;
import com.getpcpanel.integration.sonar.rest.dto.SonarStatusDto.SonarChannelDto;

import org.junit.jupiter.api.Test;

class SonarResourceTest {
    @Test
    void reportsModeReadinessAndEveryKnownLevel() {
        var client = new SonarClient(Path.of("no-such-coreProps.json")) {
            @Override public Optional<SonarMode> fetchMode() {
                return Optional.of(SonarMode.stream);
            }
        };
        var service = SonarServiceFixtures.service(client, true);
        service.replaceState(new SonarState(SonarMode.stream, Map.of(
                SonarRoute.of(SonarMode.stream, SonarMix.monitoring, SonarChannel.Game),
                new SonarLevel(0.25, true))));

        var status = new SonarResource(service).status();

        assertTrue(status.enabled());
        assertTrue(status.ready());
        assertEquals("stream", status.mode());
        assertEquals(1, status.channels().size());
        assertEquals("Game", status.channels().get(0).channel());
        assertEquals("monitoring", status.channels().get(0).mix());
        assertEquals(0.25, status.channels().get(0).volume(), 0.0001);
        assertTrue(status.channels().get(0).muted());
    }

    /** Classic mode has no mixes: {@link SonarRoute#of} drops the mix, and the DTO must report that as
     *  null rather than defaulting it to a mix name — one row per channel, not per (channel, mix). */
    @Test
    void classicModeHasNoMix() {
        var client = new SonarClient(Path.of("no-such-coreProps.json")) {
            @Override public Optional<SonarMode> fetchMode() {
                return Optional.of(SonarMode.classic);
            }
        };
        var service = SonarServiceFixtures.service(client, true);
        service.replaceState(new SonarState(SonarMode.classic, Map.of(
                SonarRoute.of(SonarMode.classic, SonarMix.monitoring, SonarChannel.Game),
                new SonarLevel(0.5, false),
                SonarRoute.of(SonarMode.classic, SonarMix.streaming, SonarChannel.Chat),
                new SonarLevel(0.75, true))));

        var status = new SonarResource(service).status();

        assertEquals("classic", status.mode());
        assertEquals(2, status.channels().size());
        status.channels().forEach(c -> assertNull(c.mix()));
        assertEquals(2, status.channels().stream().map(SonarChannelDto::channel).distinct().count());
    }
}
