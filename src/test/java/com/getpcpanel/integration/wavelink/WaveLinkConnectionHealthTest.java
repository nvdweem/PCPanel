package com.getpcpanel.integration.wavelink;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;

/**
 * The connection-liveness logic that decides whether an open Wave Link socket is actually usable. A
 * socket that reports open is not proof of a live connection: a half-open socket (no close ever
 * delivered, e.g. across a PC restart/resume) and a connect whose post-connect handshake never
 * completed both keep {@code isConnected()} true forever. Without a read-side liveness check the
 * scheduled reconnect loop never fires and every command is silently swallowed until the app is
 * restarted — which is exactly the failure this guards against.
 */
class WaveLinkConnectionHealthTest {
    /** A WaveLinkService whose connected/enabled state and reconnect calls the test controls. */
    private static class TestService extends WaveLinkService {
        boolean connected = true;
        boolean enabled = true;
        Boolean healthyOverride;
        int reconnects;

        @Override
        public boolean isConnected() {
            return connected;
        }

        @Override
        public boolean isEnabled() {
            return enabled;
        }

        @Override
        public boolean isConnectionHealthy(long nowMs) {
            return healthyOverride != null ? healthyOverride : super.isConnectionHealthy(nowMs);
        }

        @Override
        public CompletableFuture<Void> reconnect() {
            reconnects++;
            return CompletableFuture.completedFuture(null);
        }
    }

    @Test
    void unconnectedIsNeverHealthy() {
        var wl = new TestService();
        wl.connected = false;
        wl.markConnected(1_000);
        wl.setInitialized();
        assertFalse(wl.isConnectionHealthy(1_000));
    }

    @Test
    void initialisedConnectionWithRecentActivityIsHealthy() {
        var wl = new TestService();
        wl.markConnected(0);
        wl.setInitialized();
        wl.recordInboundActivity(0);
        // Well within the inbound-inactivity window (35s).
        assertTrue(wl.isConnectionHealthy(5_000));
    }

    @Test
    void openSocketWithNoInboundFramesGoesStale() {
        var wl = new TestService();
        wl.markConnected(0);
        wl.setInitialized();
        wl.recordInboundActivity(0);
        // No pong/message for well beyond the 35s window: the peer is gone though the socket looks open.
        assertFalse(wl.isConnectionHealthy(40_000));
    }

    @Test
    void freshInboundFrameRevivesAStalledConnection() {
        var wl = new TestService();
        wl.markConnected(0);
        wl.setInitialized();
        wl.recordInboundActivity(0);
        assertFalse(wl.isConnectionHealthy(40_000));
        // A pong lands: activity is fresh again, so the connection is healthy once more.
        wl.recordInboundActivity(40_000);
        assertTrue(wl.isConnectionHealthy(41_000));
    }

    @Test
    void connectedButUninitialisedIsHealthyDuringHandshakeGrace() {
        var wl = new TestService();
        wl.markConnected(0);
        wl.recordInboundActivity(5_000);
        // Handshake still in flight, but within the grace window (20s) and traffic is flowing.
        assertTrue(wl.isConnectionHealthy(10_000));
    }

    @Test
    void connectedButUninitialisedPastGraceIsUnhealthy() {
        var wl = new TestService();
        wl.markConnected(0);
        // Traffic is even fresh, but the handshake never completed within the grace window.
        wl.recordInboundActivity(24_000);
        assertFalse(wl.isConnectionHealthy(25_000));
    }

    /**
     * The write side has its own way of dying: the socket keeps delivering pongs and pushes while
     * nothing we send reaches Wave Link. Inbound liveness alone reports that as perfectly healthy, so
     * every command is swallowed by a connection nobody reconnects — the failure only a restart cleared.
     */
    @Test
    void requestsThatNeverGetAnsweredMakeAnInboundLiveConnectionUnhealthy() {
        var wl = new TestService();
        wl.markConnected(0);
        wl.setInitialized();
        wl.recordInboundActivity(30_000); // pongs still arriving: inbound liveness is untouched
        assertTrue(wl.isConnectionHealthy(31_000));

        wl.recordRequestUnanswered();
        assertTrue(wl.isConnectionHealthy(31_000), "a single unanswered request can be a hiccup");

        wl.recordRequestUnanswered();
        assertFalse(wl.isConnectionHealthy(31_000),
                "commands that never reach Wave Link mean the connection is not usable, however alive it reads");
    }

    @Test
    void anAnsweredRequestClearsTheUnansweredRun() {
        var wl = new TestService();
        wl.markConnected(0);
        wl.setInitialized();
        wl.recordInboundActivity(30_000);
        wl.recordRequestUnanswered();
        wl.recordRequestUnanswered();
        assertFalse(wl.isConnectionHealthy(31_000));

        wl.recordRequestAnswered(); // Wave Link answered: the link carries commands again
        assertTrue(wl.isConnectionHealthy(31_000));
    }

    @Test
    void reconnectingClearsTheUnansweredRun() {
        var wl = new TestService();
        wl.markConnected(0);
        wl.setInitialized();
        wl.recordInboundActivity(30_000);
        wl.recordRequestUnanswered();
        wl.recordRequestUnanswered();

        // A fresh socket must not inherit the dead one's tally, or it is condemned before it is used.
        wl.markDisconnected();
        wl.markConnected(31_000);
        wl.setInitialized();
        assertTrue(wl.isConnectionHealthy(31_000));
    }

    @Test
    void checkConnectionReconnectsAnOpenButUnhealthyConnection() {
        var wl = new TestService();
        wl.connected = true;
        wl.healthyOverride = false;
        wl.checkConnection();
        assertEquals(1, wl.reconnects, "an open-but-unresponsive connection must be reconnected");
    }

    @Test
    void checkConnectionDoesNotReconnectAHealthyConnection() {
        var wl = new TestService();
        wl.connected = true;
        wl.healthyOverride = true;
        wl.checkConnection();
        assertEquals(0, wl.reconnects, "a healthy connection must not be torn down");
    }
}
