package com.getpcpanel.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.getpcpanel.hid.DeviceCommunicationHandler;
import com.getpcpanel.hid.DeviceHolder;
import com.getpcpanel.hid.DeviceScanner;
import com.getpcpanel.util.AppShutdownState;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import lombok.extern.log4j.Log4j2;

@Log4j2
@ApplicationScoped
public class EventBroadcaster {
    @Inject ObjectMapper objectMapper;

    private boolean shouldSkipBroadcast() {
        return AppShutdownState.isShuttingDown();
    }

    private void broadcast(Object event) {
        if (shouldSkipBroadcast())
            return;
        EventWebSocket.broadcast(event, objectMapper);
    }

    public void onDeviceConnected(@Observes DeviceHolder.DeviceFullyConnectedEvent event) {
        broadcast(new WsEvent("device_connected", event.device().getSerialNumber()));
    }

    public void onDeviceDisconnected(@Observes DeviceScanner.DeviceDisconnectedEvent event) {
        broadcast(new WsEvent("device_disconnected", event.serialNum()));
    }

    public void onKnobRotate(@Observes DeviceCommunicationHandler.KnobRotateEvent event) {
        broadcast(new WsKnobEvent("knob_rotate", event.serialNum(), event.knob(), event.value()));
    }

    public void onButtonPress(@Observes DeviceCommunicationHandler.ButtonPressEvent event) {
        broadcast(new WsButtonEvent("button_press", event.serialNum(), event.button(), event.pressed()));
    }

    public record WsEvent(String type, String serial) {
    }

    public record WsKnobEvent(String type, String serial, int knob, int value) {
    }

    public record WsButtonEvent(String type, String serial, int button, boolean pressed) {
    }
}
