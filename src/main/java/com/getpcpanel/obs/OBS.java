package com.getpcpanel.obs;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.getpcpanel.profile.SaveService;

import io.quarkus.scheduler.Scheduled;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
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

    @Inject SaveService save;
    @Inject Event<OBSConnectEvent> connectEvent;
    @Inject Event<OBSMuteEvent> muteEvent;
    @Inject ObjectMapper objectMapper;

    private ObsWebSocketClient client;

    @PostConstruct
    public void init() {
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
            return;
        }
        if (client != null && client.isConnected()) {
            return;
        }
        connect(settings.getObsAddress(), parsePort(settings.getObsPort()), settings.getObsPassword());
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

