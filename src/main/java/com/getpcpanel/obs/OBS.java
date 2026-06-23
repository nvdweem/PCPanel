package com.getpcpanel.obs;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.getpcpanel.profile.SaveService;
import com.getpcpanel.profile.SaveService.SaveEvent;
import com.getpcpanel.util.ReconnectBackoff;

import io.quarkus.scheduler.Scheduled;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import lombok.extern.log4j.Log4j2;

/**
 * OBS integration via a custom OBS WebSocket 5 client.
 *
 * <p>Connects automatically when {@code obsEnabled=true} in the user's settings.
 * Reconnects every 30 s if the connection is lost.
 * Fires {@link OBSConnectEvent} and {@link OBSMuteEvent} CDI events.
 */
@Log4j2
@ApplicationScoped
public final class OBS {
    private static final long CONNECT_TIMEOUT_MS = 5_000;
    /** Spaces out reconnect attempts when OBS is down (base = the scheduled interval, capped at 5 min). */
    private final ReconnectBackoff backoff = new ReconnectBackoff(30_000, 300_000);

    @Inject SaveService save;
    @Inject Event<OBSConnectEvent> connectEvent;
    @Inject Event<OBSMuteEvent> muteEvent;
    @Inject ObjectMapper objectMapper;

    private ObsWebSocketClient client;
    /** Last-applied OBS config (enabled + address/port/password), so an unrelated save never drops a healthy connection. */
    private String appliedConfig;
    /** Dev/test override: when non-null, the source mute state reported to the mute-colour layer (see {@link #simulateSourceMute}). */
    private volatile Map<String, Boolean> simulatedMuteState;
    /** Cached source-&gt;muted state, fed by the on-connect snapshot + every InputMuteStateChanged push, so
     *  the mute-colour layer reads it without a blocking OBS round-trip (see {@link #getSourcesWithMuteState}). */
    private final Map<String, Boolean> muteStateCache = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Apply OBS connection changes the moment settings are saved — including the initial SaveEvent
     * fired at startup, which is what makes OBS connect on launch. Without observing SaveEvent the
     * bean is only created lazily on the first scheduled tick, so the initial connection (and any
     * enable/host/port/password change) would be delayed by up to one 30s interval. The scheduler
     * still owns periodic reconnect when OBS drops.
     */
    // synchronized: the SaveEvent observer (REST/Debouncer thread) and the @Scheduled tick (scheduler
    // thread) both mutate `client`/`appliedConfig` via connect(). Without serialization the two connect()
    // calls interleave and one freshly-created, open websocket client is overwritten and leaked.
    public synchronized void settingsChanged(@Observes SaveEvent event) {
        var settings = event.save();
        var config = settings.isObsEnabled() + "|" + settings.getObsAddress() + "|" + settings.getObsPort() + "|" + settings.getObsPassword();
        if (config.equals(appliedConfig)) {
            return;
        }
        appliedConfig = config;
        if (client != null) {
            client.disconnect();
            client = null;
        }
        backoff.onSuccess(); // clear the gate so the new settings connect immediately
        reconnectIfNeeded();
    }

    @PreDestroy
    public synchronized void destroy() {
        if (client != null) {
            client.disconnect();
        }
    }

    @Scheduled(every = "30s")
    public synchronized void reconnectIfNeeded() {
        var settings = save.get();
        if (!settings.isObsEnabled()) {
            backoff.onSuccess(); // nothing to connect → keep the gate clear so enabling reconnects at once
            return;
        }
        if (client != null && client.isConnected()) {
            backoff.onSuccess();
            return;
        }
        if (!backoff.ready(System.currentTimeMillis())) {
            return;
        }
        connect(settings.getObsAddress(), parsePort(settings.getObsPort()), settings.getObsPassword());
        // OBS authentication completes asynchronously, so we can't tell success here; record an attempt
        // and let the next tick's isConnected() check clear the backoff once the handshake completes.
        backoff.onFailure(System.currentTimeMillis());
    }

    private synchronized void connect(String host, int port, String password) {
        try {
            if (client != null) {
                client.disconnect();
            }
            client = new ObsWebSocketClient(objectMapper, password,
                    this::onConnectionChanged,
                    event -> {
                        muteStateCache.put(event.input(), event.muted());
                        muteEvent.fire(event);
                    });
            client.connect(host, port, CONNECT_TIMEOUT_MS);
            log.info("OBS: connecting to {}:{}", host, port);
        } catch (Exception e) {
            log.debug("OBS: connection attempt failed: {}", e.getMessage());
        }
    }

    /** Connection-state callback from the websocket client (its own thread). */
    private void onConnectionChanged(boolean connected) {
        connectEvent.fire(new OBSConnectEvent(connected));
        if (connected) {
            var c = client;
            if (c != null) {
                // Snapshot the initial mute state OFF the websocket thread: getSourcesWithMuteState() is a
                // blocking request-response, which would deadlock if run on the ws read thread and froze the
                // audio-event thread when it used to run there (the bug this whole cache fixes).
                java.util.concurrent.CompletableFuture.runAsync(() -> primeMuteCache(c));
            }
        } else {
            muteStateCache.clear();
        }
    }

    /** One-time initial mute-state snapshot after connect; then InputMuteStateChanged keeps the cache fresh. */
    private void primeMuteCache(ObsWebSocketClient c) {
        try {
            var snapshot = c.getSourcesWithMuteState();
            muteStateCache.keySet().retainAll(snapshot.keySet());
            snapshot.forEach((source, muted) -> {
                muteStateCache.put(source, muted);
                // Now the state is known, drive a mute-colour recompute (the connect-time recompute likely
                // ran before this async snapshot landed).
                muteEvent.fire(new OBSMuteEvent(source, muted));
            });
        } catch (RuntimeException e) {
            log.debug("OBS: initial mute-state snapshot failed: {}", e.getMessage());
        }
    }

    private static int parsePort(String port) {
        try {
            return Integer.parseInt(port);
        } catch (NumberFormatException e) {
            return 4455;
        }
    }

    // --- Public API used by command classes ---

    public boolean isConnected() {
        return client != null && client.isConnected();
    }

    public List<String> getSourcesWithAudio() {
        return isConnected() ? client.getSourcesWithAudio() : List.of();
    }

    public Map<String, Boolean> getSourcesWithMuteState() {
        var simulated = simulatedMuteState;
        if (simulated != null) {
            return simulated;
        }
        // Read the cache, never a live OBS round-trip: this is called from the mute-colour recompute that
        // runs on native-audio / Wave Link / VoiceMeeter event threads, which must never block on OBS.
        return isConnected() ? Map.copyOf(muteStateCache) : Map.of();
    }

    /**
     * Dev/test hook: report {@code source} as muted/unmuted and fire {@link OBSMuteEvent} through the
     * normal path, exactly as an OBS {@code InputMuteStateChanged} event would — so the mute-override
     * colour can be exercised without a real OBS connection. Mirrors Wave Link's channel-state sim.
     */
    public void simulateSourceMute(String source, boolean muted) {
        var state = simulatedMuteState;
        if (state == null) {
            state = new java.util.concurrent.ConcurrentHashMap<>();
            simulatedMuteState = state;
        }
        state.put(source, muted);
        muteEvent.fire(new OBSMuteEvent(source, muted));
    }

    public List<String> getScenes() {
        return isConnected() ? client.getScenes() : List.of();
    }

    public void setSourceVolume(String sourceName, int vol) {
        if (isConnected()) {
            client.setSourceVolume(sourceName, vol);
        }
    }

    public void toggleSourceMute(String sourceName) {
        if (isConnected()) {
            client.toggleSourceMute(sourceName);
        }
    }

    public void setSourceMute(String sourceName, boolean mute) {
        if (isConnected()) {
            client.setSourceMute(sourceName, mute);
        }
    }

    public void setCurrentScene(String sceneName) {
        if (isConnected()) {
            client.setCurrentScene(sceneName);
        }
    }

    /** Performs a no-payload OBS request (e.g. {@code StartStream}, {@code ToggleRecord}, {@code StartVirtualCam}). */
    public void performAction(String requestType) {
        if (isConnected()) {
            client.performAction(requestType);
        }
    }

    /** Returns null on success, or an error message on failure. */
    public String test(String address, int port, String password, long timeout) {
        var tester = new ObsWebSocketClient(objectMapper, password, c -> {}, e -> {});
        try {
            tester.connect(address, port, timeout);
            Thread.sleep(500); // allow hello/identify exchange
            return tester.isConnected() ? null : "Connected but not authenticated";
        } catch (Exception e) {
            return e.getMessage();
        } finally {
            tester.disconnect();
        }
    }
}

