package com.getpcpanel.integration.sonar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class SonarServicePollTest {
    /** Counts mode reads and level reads so the gates can be asserted without a network. */
    private static class CountingClient extends SonarClient {
        final AtomicInteger modeFetches = new AtomicInteger();
        final AtomicInteger fetches = new AtomicInteger();
        SonarState state = new SonarState(SonarMode.stream, Map.of(
                SonarRoute.of(SonarMode.stream, SonarMix.monitoring, SonarChannel.Game),
                new SonarLevel(0.5, false)));

        CountingClient() {
            super(Path.of("no-such-coreProps.json"));
        }

        @Override public Optional<SonarMode> fetchMode() {
            modeFetches.incrementAndGet();
            return Optional.of(SonarMode.stream);
        }

        @Override public Optional<SonarState> fetchState(SonarMode mode) {
            fetches.incrementAndGet();
            return Optional.of(state);
        }
    }

    /** A client whose mode can be pulled out from under the service, as when GG closes Sonar mid-session. */
    private static class ModeTogglingClient extends SonarClient {
        boolean modeAvailable = true;
        final SonarState state = new SonarState(SonarMode.stream, Map.of(
                SonarRoute.of(SonarMode.stream, SonarMix.monitoring, SonarChannel.Game),
                new SonarLevel(0.5, false)));

        ModeTogglingClient() {
            super(Path.of("no-such-coreProps.json"));
        }

        @Override public Optional<SonarMode> fetchMode() {
            return modeAvailable ? Optional.of(SonarMode.stream) : Optional.empty();
        }

        @Override public Optional<SonarState> fetchState(SonarMode mode) {
            return Optional.of(state);
        }
    }

    @Test
    void doesNotPollWhileDisabled() {
        var client = new CountingClient();
        var service = SonarServiceFixtures.service(client, false);
        service.setInUse(true);

        service.poll();

        assertEquals(0, client.modeFetches.get());
        assertEquals(0, client.fetches.get());
        assertFalse(service.isEnabled());
    }

    @Test
    void pollsAndCachesWhenEnabledAndInUse() {
        var client = new CountingClient();
        var service = SonarServiceFixtures.service(client, true);
        service.setInUse(true);

        service.poll();

        assertEquals(1, client.fetches.get());
        assertTrue(service.isReady());
        assertEquals(0.5, service.level(SonarChannel.Game, SonarMix.monitoring).orElseThrow().volume(), 0.0001);
    }

    @Test
    void findsSonarButReadsNoLevelsWhenEnabledAndNothingUsesIt() {
        var client = new CountingClient();
        var service = SonarServiceFixtures.service(client, true);
        service.setInUse(false);

        service.poll();

        assertTrue(service.isReady(), "an enabled Sonar is found before any control targets it");
        assertEquals(SonarMode.stream, service.snapshot().mode());
        assertEquals(1, client.modeFetches.get());
        assertEquals(0, client.fetches.get(), "the full level read waits for a control to use it");
    }

    @Test
    void levelsAreDroppedOnceNothingUsesThem() {
        var client = new CountingClient();
        var service = SonarServiceFixtures.service(client, true);
        service.setInUse(true);
        service.poll();
        assertTrue(service.level(SonarChannel.Game, SonarMix.monitoring).isPresent(), "precondition: levels were read");

        service.setInUse(false);
        service.poll();

        assertTrue(service.isReady());
        assertTrue(service.snapshot().levels().isEmpty(), "no stale levels are kept once they stop being refreshed");
    }

    @Test
    void modeGoingAwayResetsReadiness() {
        var client = new ModeTogglingClient();
        var service = SonarServiceFixtures.service(client, true);
        service.setInUse(true);

        service.poll();
        assertTrue(service.isReady());

        client.modeAvailable = false;
        service.poll();

        assertFalse(service.isReady());
        assertTrue(service.level(SonarChannel.Game, SonarMix.monitoring).isEmpty());
    }

    @Test
    void unchangedStateDoesNotFireAgain() {
        var client = new CountingClient();
        var changed = new SonarServiceFixtures.RecordingEvent();
        var service = SonarServiceFixtures.service(client, true, changed);
        service.setInUse(true);

        service.poll();
        assertEquals(1, changed.fired.get());

        service.poll();
        assertEquals(1, changed.fired.get());
    }

    @Test
    void changedStateFiresAgain() {
        var client = new CountingClient();
        var changed = new SonarServiceFixtures.RecordingEvent();
        var service = SonarServiceFixtures.service(client, true, changed);
        service.setInUse(true);

        service.poll();
        assertEquals(1, changed.fired.get());

        client.state = new SonarState(SonarMode.stream, Map.of(
                SonarRoute.of(SonarMode.stream, SonarMix.monitoring, SonarChannel.Game),
                new SonarLevel(0.75, false)));
        service.poll();

        assertEquals(2, changed.fired.get());
    }
}
