package com.getpcpanel.rest;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.getpcpanel.hid.DeviceCommunicationHandler;
import com.getpcpanel.hid.DeviceHolder;
import com.getpcpanel.hid.DeviceScanner;

import lombok.extern.log4j.Log4j2;

@Log4j2
@ApplicationScoped
public class EventBroadcaster {
    @Inject ObjectMapper objectMapper;

    public void onDeviceConnected(@Observes DeviceHolder.DeviceFullyConnectedEvent event) {
        EventWebSocket.broadcast(
                new WsEvent("device_connected", event.device().getSerialNumber()),
                objectMapper);
    }

    public void onDeviceDisconnected(@Observes DeviceScanner.DeviceDisconnectedEvent event) {
        EventWebSocket.broadcast(
                new WsEvent("device_disconnected", event.serialNum()),
                objectMapper);
    }

    public void onKnobRotate(@Observes DeviceCommunicationHandler.KnobRotateEvent event) {
        EventWebSocket.broadcast(
                new WsKnobEvent("knob_rotate", event.serialNum(), event.knob(), event.value()),
                objectMapper);
    }

    public void onButtonPress(@Observes DeviceCommunicationHandler.ButtonPressEvent event) {
        EventWebSocket.broadcast(
                new WsButtonEvent("button_press", event.serialNum(), event.button(), event.pressed()),
                objectMapper);
    }

    public record WsEvent(String type, String serial) {}
    public record WsKnobEvent(String type, String serial, int knob, int value) {}
    public record WsButtonEvent(String type, String serial, int button, boolean pressed) {}
}
