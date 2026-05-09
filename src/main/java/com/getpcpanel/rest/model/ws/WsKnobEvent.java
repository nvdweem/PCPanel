package com.getpcpanel.rest.model.ws;

import com.fasterxml.jackson.annotation.JsonTypeName;

@JsonTypeName("knob_rotate")
public record WsKnobEvent(String serial, int knob, int value) implements WsEvent {
}
