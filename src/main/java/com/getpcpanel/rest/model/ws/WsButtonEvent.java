package com.getpcpanel.rest.model.ws;

import com.fasterxml.jackson.annotation.JsonTypeName;

@JsonTypeName("button_press")
public record WsButtonEvent(String serial, int button, boolean pressed) implements WsEvent {
}
