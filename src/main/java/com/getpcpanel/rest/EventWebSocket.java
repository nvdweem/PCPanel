package com.getpcpanel.rest;

import com.getpcpanel.device.provider.pcpanel.ProVisualColorsService;
import java.time.Duration;
import java.util.concurrent.CopyOnWriteArraySet;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.getpcpanel.device.DeviceHolder;
import com.getpcpanel.profile.SaveService;
import com.getpcpanel.rest.auth.SessionAuthFilter;
import com.getpcpanel.rest.auth.SessionTokenService;
import com.getpcpanel.rest.model.dto.DeviceSnapshotDto;
import com.getpcpanel.rest.model.ws.WsDeviceConnectedEvent;
import com.getpcpanel.util.app.AppShutdownState;

import io.quarkus.websockets.next.OnClose;
import io.quarkus.websockets.next.OnOpen;
import io.quarkus.websockets.next.WebSocket;
import io.quarkus.websockets.next.WebSocketConnection;
import jakarta.inject.Inject;
import lombok.extern.log4j.Log4j2;

@Log4j2
@WebSocket(path = "/ws/events")
public class EventWebSocket {
    private static final CopyOnWriteArraySet<WebSocketConnection> connections = new CopyOnWriteArraySet<>();

    @Inject ObjectMapper objectMapper;
    @Inject DeviceHolder deviceHolder;
    @Inject SaveService saveService;
    @Inject ProVisualColorsService proVisualColorsService;
    @Inject EventBroadcaster eventBroadcaster;
    @Inject SessionTokenService sessionTokens;

    @ConfigProperty(name = "pcpanel.http.require-session", defaultValue = "true")
    boolean requireSession;

    @OnOpen
    public void onOpen(WebSocketConnection connection) {
        if (AppShutdownState.isShuttingDown()) {
            log.debug("Ignoring websocket connection {} because shutdown is in progress", connection.id());
            return;
        }
        var handshake = connection.handshakeRequest();
        if (!LocalHttpGuard.hostAllowed(handshake.header("Host")) || !LocalHttpGuard.originAllowed(handshake.header("Origin"))) {
            log.debug("Rejecting non-local websocket connection {} (Host={}, Origin={})", connection.id(), handshake.header("Host"), handshake.header("Origin"));
            connection.closeAndAwait();
            return;
        }
        // Second layer, mirroring the REST session filter: the browser auto-sends the session cookie on
        // the WS handshake, so an unauthenticated local process cannot open the event socket either.
        if (requireSession) {
            var token = SessionAuthFilter.sessionTokenFrom(handshake.header("Cookie")).orElse(null);
            if (!sessionTokens.isValidSession(token)) {
                log.debug("Rejecting websocket connection {}: missing or invalid session cookie", connection.id());
                connection.closeAndAwait();
                return;
            }
        }
        connections.add(connection);
        log.debug("WebSocket client connected: {} (total connections: {})", connection.id(), connections.size());
        sendInitialSnapshots(connection);
    }

    @OnClose
    public void onClose(WebSocketConnection connection) {
        connections.remove(connection);
        log.debug("WebSocket client disconnected: {} (remaining connections: {})", connection.id(), connections.size());
    }

    private void sendInitialSnapshots(WebSocketConnection connection) {
        var save = saveService.get();
        deviceHolder.all().forEach(device -> {
            try {
                var deviceSave = save.getDeviceSave(device.getSerialNumber());
                if (deviceSave == null) {
                    log.debug("Skipping initial device_connected for {} because no device save exists", device.getSerialNumber());
                    return;
                }

                var snapshot = DeviceSnapshotDto.from(device, deviceSave, proVisualColorsService);
                var connectedEvent = new WsDeviceConnectedEvent(snapshot);
                var json = objectMapper.writeValueAsString(connectedEvent);
                connection.sendTextAndAwait(json);
            } catch (Exception e) {
                log.warn("Failed to send initial device_connected for {} to new WS connection {}", device.getSerialNumber(), connection.id(), e);
            }
        });

        // Replay any new-version notice detected at startup, since the check may have finished
        // before this client connected and one-shot broadcasts would otherwise be missed.
        var newVersion = eventBroadcaster.latestNewVersion();
        if (newVersion != null) {
            try {
                connection.sendTextAndAwait(objectMapper.writeValueAsString(newVersion));
            } catch (Exception e) {
                log.warn("Failed to send new-version notice to new WS connection {}", connection.id(), e);
            }
        }
    }

    /**
     * Sends one prepared frame to every connected client, waiting at most {@code timeout} on each.
     *
     * <p>Only {@link WebSocketBroadcastQueue}'s dispatcher thread calls this — never a thread that
     * produced an event, which is the whole point: a client that has stopped reading must not be able
     * to hold the HID input thread, the command thread, or the audio backend's notification threads.
     * The timeout keeps one such client from holding up the others' updates indefinitely too.
     */
    static void sendToAll(String json, Duration timeout) {
        if (AppShutdownState.isShuttingDown()) {
            connections.clear();
            return;
        }
        log.debug("Broadcasting event to {} WebSocket clients: {}", connections.size(), json);
        connections.forEach(c -> {
            try {
                c.sendText(json).await().atMost(timeout);
            } catch (Exception e) {
                log.debug("Failed to send event to WS client {}", c.id(), e);
            }
        });
    }
}
