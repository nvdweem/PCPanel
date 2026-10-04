package com.getpcpanel.rest.model.ws;

import com.fasterxml.jackson.annotation.JsonTypeName;

@JsonTypeName("history_changed")
public record WsHistoryChangedEvent(boolean canUndo, boolean canRedo) implements WsEvent {
}
