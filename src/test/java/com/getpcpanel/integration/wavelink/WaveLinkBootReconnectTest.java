package com.getpcpanel.integration.wavelink;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CompletableFuture;

import javax.annotation.Nullable;

import org.junit.jupiter.api.Test;

import dev.niels.wavelink.impl.WaveLinkClientImpl.WaveLinkEndpoint;

/**
 * The cold-boot reconnect gate. Wave Link starts long after PCPanel does — it is a packaged app whose
 * audio server and UI come up over a minute into a fresh boot — so the reconnect loop always spends its
 * first attempts against a Wave Link that is not listening yet, and the backoff grows. The gate must not
 * then sit out that delay once Wave Link is finally up: it advertises its port in {@code ws-info.json}
 * when it starts, so a new stamp there is proof a live Wave Link is waiting and the remaining wait is
 * meaningless. Without that signal the only way back is restarting PCPanel, which resets the backoff —
 * the "Wave Link is dead until I restart PCPanel" report.
 *
 * <p>The endpoint tests all act at a single instant, with no time passing between the closed gate and
 * the retry, so they measure only the endpoint signal and stay valid whatever the backoff is tuned to.
 */
class WaveLinkBootReconnectTest {
    /** Long enough for the backoff to reach its cap while Wave Link is still booting. */
    private static final long SETTLED = 1_000_000L;

    /** A WaveLinkService that is permanently down, with a test-controlled advertised endpoint. */
    private static class TestService extends WaveLinkService {
        int reconnects;
        boolean healthy;
        @Nullable WaveLinkEndpoint endpoint = new WaveLinkEndpoint(49316, 1_000L);

        @Override
        public boolean isConnected() {
            return healthy;
        }

        @Override
        public boolean isEnabled() {
            return true;
        }

        @Override
        public boolean isConnectionHealthy(long nowMs) {
            return healthy;
        }

        @Override
        @Nullable
        public WaveLinkEndpoint readEndpoint() {
            return endpoint;
        }

        @Override
        public CompletableFuture<Void> reconnect() {
            reconnects++;
            return CompletableFuture.completedFuture(null);
        }
    }

    /** Drives the 10s scheduled check up to {@code untilMs}, as the scheduler would. */
    private static void tick(TestService wl, long untilMs) {
        for (var now = 0L; now <= untilMs; now += 10_000) {
            wl.checkConnection(now);
        }
    }

    /** A service that has been ticking against a down Wave Link until its gate is shut at {@link #SETTLED}. */
    private static TestService downWithClosedGate() {
        var wl = new TestService();
        tick(wl, SETTLED);
        var attempts = wl.reconnects;
        wl.checkConnection(SETTLED);
        assertEquals(attempts, wl.reconnects, "precondition: the backoff gate must be shut at this instant");
        return wl;
    }

    @Test
    void aDownWaveLinkIsNotRetriedOnEveryTick() {
        var wl = new TestService();
        tick(wl, SETTLED); // 101 ticks against a Wave Link that never comes up

        assertTrue(wl.reconnects <= 25,
                "a service that is down must be backed off, not hammered once per tick; attempts: " + wl.reconnects);
    }

    @Test
    void aNewlyAdvertisedEndpointIsRetriedWithoutWaitingOutTheBackoff() {
        var wl = downWithClosedGate();
        var attempts = wl.reconnects;

        // Wave Link finishes starting and advertises the port it is now listening on.
        wl.endpoint = new WaveLinkEndpoint(55777, SETTLED);
        wl.checkConnection(SETTLED);

        assertEquals(attempts + 1, wl.reconnects,
                "a freshly advertised endpoint proves Wave Link is up now — it must be tried immediately");
    }

    @Test
    void aReadvertisedEndpointOnTheSamePortIsAlsoRetried() {
        var wl = downWithClosedGate();
        var attempts = wl.reconnects;

        // Wave Link restarted and happened to get the same port back: only the write time moved.
        wl.endpoint = new WaveLinkEndpoint(49316, SETTLED);
        wl.checkConnection(SETTLED);

        assertEquals(attempts + 1, wl.reconnects,
                "the port can repeat across restarts, so the publish time must count as a new endpoint too");
    }

    @Test
    void anUnchangedEndpointDoesNotClearTheBackoff() {
        var wl = downWithClosedGate();
        var attempts = wl.reconnects;

        wl.checkConnection(SETTLED); // same stale descriptor: nothing says Wave Link came up

        assertEquals(attempts, wl.reconnects,
                "an unchanged endpoint is no evidence of a live Wave Link and must not defeat the backoff");
    }

    @Test
    void anAbsentEndpointDescriptorDoesNotClearTheBackoff() {
        var wl = downWithClosedGate();
        var attempts = wl.reconnects;

        wl.endpoint = null; // no descriptor on disk (Wave Link not installed, or unreadable)
        wl.checkConnection(SETTLED);

        assertEquals(attempts, wl.reconnects, "a missing descriptor must not be read as a new endpoint");
    }

    @Test
    void connectingClearsTheBackoffSoALaterDropRetriesAtOnce() {
        var wl = downWithClosedGate();
        var attempts = wl.reconnects;

        // Wave Link answers: the gate clears, so the next drop is retried on the following tick.
        wl.healthy = true;
        wl.checkConnection(SETTLED);
        wl.healthy = false;
        wl.checkConnection(SETTLED + 10_000);

        assertEquals(attempts + 1, wl.reconnects, "a connection that succeeded must leave the gate clear");
    }
}
