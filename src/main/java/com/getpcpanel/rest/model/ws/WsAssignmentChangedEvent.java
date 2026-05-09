package com.getpcpanel.rest.model.ws;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.getpcpanel.commands.Commands;
import com.getpcpanel.rest.EventBroadcaster.AssignmentChangedEvent.Kinds;

@JsonTypeName("assignment_changed")
public record WsAssignmentChangedEvent(String serial, Kinds kind, int index, Commands commands) implements WsEvent {
}
