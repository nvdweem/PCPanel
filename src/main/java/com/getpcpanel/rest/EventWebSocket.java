package com.getpcpanel.rest;

import java.util.concurrent.CopyOnWriteArraySet;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.getpcpanel.hid.DeviceCommunicationHandler;
import com.getpcpanel.hid.DeviceHolder;
import com.getpcpanel.hid.DeviceScanner;

import io.quarkus.websockets.next.OnClose;
import io.quarkus.websockets.next.OnOpen;
import io.quarkus.websockets.next.WebSocket;
import io.quarkus.websockets.next.WebSocketConnection;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Log4j2
@WebSocket(path = "/ws/events")
@RequiredArgsConstructor
public class EventWebSocket {
    private static final CopyOnWriteArraySet<WebSocketConnection> connections = new CopyOnWriteArraySet<>();

    private final ObjectMapper objectMapper;

    @OnOpen
    public void onOpen(WebSocketConnection connection) {
        connections.add(connection);
    }

    @OnClose
    public void onClose(WebSocketConnection connection) {
        connections.remove(connection);
    }

    public static void broadcast(Object event, ObjectMapper mapper) {
        try {
            var json = mapper.writeValueAsString(event);
            connections.forEach(c -> {
                try {
                    c.sendTextAndAwait(json);
                } catch (Exception e) {
                    log.debug("Failed to send event to WS client", e);
                }
            });
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize event", e);
        }
    }
}
