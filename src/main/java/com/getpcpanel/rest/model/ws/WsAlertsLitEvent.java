package com.getpcpanel.rest.model.ws;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonTypeName;

/** The notification lights showing now, as positions in the settings' notification light list. */
@JsonTypeName("alerts_lit")
public record WsAlertsLitEvent(List<Integer> indexes) implements WsEvent {
}
