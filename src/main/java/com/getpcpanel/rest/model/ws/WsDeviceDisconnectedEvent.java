package com.getpcpanel.rest.model.ws;

import com.fasterxml.jackson.annotation.JsonTypeName;

@JsonTypeName("device_disconnected")
public record WsDeviceDisconnectedEvent(String serial) implements WsEvent {
}
