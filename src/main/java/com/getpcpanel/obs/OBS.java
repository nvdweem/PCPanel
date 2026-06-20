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

    /**
     * Apply OBS connection changes the moment settings are saved — including the initial SaveEvent
     * fired at startup, which is what makes OBS connect on launch. Without observing SaveEvent the
     * bean is only created lazily on the first scheduled tick, so the initial connection (and any
     * enable/host/port/password change) would be delayed by up to one 30s interval. The scheduler
     * still owns periodic reconnect when OBS drops.
     */
    public void settingsChanged(@Observes SaveEvent event) {
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
    public void destroy() {
        if (client != null) {
            client.disconnect();
        }
    }

    @Scheduled(every = "30s")
    public void reconnectIfNeeded() {
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

    private void connect(String host, int port, String password) {
        try {
            if (client != null) {
                client.disconnect();
            }
            client = new ObsWebSocketClient(objectMapper, password,
                    connected -> connectEvent.fire(new OBSConnectEvent(connected)),
                    event -> muteEvent.fire(event));
            client.connect(host, port, CONNECT_TIMEOUT_MS);
            log.info("OBS: connecting to {}:{}", host, port);
        } catch (Exception e) {
            log.debug("OBS: connection attempt failed: {}", e.getMessage());
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
        return isConnected() ? client.getSourcesWithMuteState() : Map.of();
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

