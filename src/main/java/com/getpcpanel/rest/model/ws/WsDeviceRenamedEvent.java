package com.getpcpanel.rest.model.ws;

import com.fasterxml.jackson.annotation.JsonTypeName;

@JsonTypeName("device_renamed")
public record WsDeviceRenamedEvent(String serial, String displayName) implements WsEvent {
}
