package com.getpcpanel.rest;

import java.util.concurrent.CopyOnWriteArraySet;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.getpcpanel.hid.DeviceHolder;
import com.getpcpanel.profile.SaveService;
import com.getpcpanel.rest.model.dto.DeviceSnapshotDto;
import com.getpcpanel.rest.model.ws.WsDeviceConnectedEvent;
import com.getpcpanel.util.AppShutdownState;

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

    @OnOpen
    public void onOpen(WebSocketConnection connection) {
        if (AppShutdownState.isShuttingDown()) {
            log.debug("Ignoring websocket connection {} because shutdown is in progress", connection.id());
            return;
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
    }

    public static void broadcast(Object event, ObjectMapper mapper) {
        if (AppShutdownState.isShuttingDown()) {
            connections.clear();
            return;
        }
        try {
            var json = mapper.writeValueAsString(event);
            log.debug("Broadcasting event to {} WebSocket clients: {}", connections.size(), json);
            connections.forEach(c -> {
                try {
                    c.sendTextAndAwait(json);
                } catch (Exception e) {
                    log.debug("Failed to send event to WS client {}", c.id(), e);
                }
            });
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize event", e);
        }
    }
}
