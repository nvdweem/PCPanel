package com.getpcpanel.rest;

import java.util.concurrent.CopyOnWriteArraySet;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.getpcpanel.util.AppShutdownState;

import io.quarkus.websockets.next.OnClose;
import io.quarkus.websockets.next.OnOpen;
import io.quarkus.websockets.next.WebSocket;
import io.quarkus.websockets.next.WebSocketConnection;
import lombok.extern.log4j.Log4j2;

@Log4j2
@WebSocket(path = "/ws/events")
public class EventWebSocket {
    private static final CopyOnWriteArraySet<WebSocketConnection> connections = new CopyOnWriteArraySet<>();

    @OnOpen
    public void onOpen(WebSocketConnection connection) {
        if (AppShutdownState.isShuttingDown()) {
            log.debug("Ignoring websocket connection {} because shutdown is in progress", connection.id());
            return;
        }
        connections.add(connection);
        log.debug("WebSocket client connected: {} (total connections: {})", connection.id(), connections.size());
    }

    @OnClose
    public void onClose(WebSocketConnection connection) {
        connections.remove(connection);
        log.debug("WebSocket client disconnected: {} (remaining connections: {})", connection.id(), connections.size());
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
