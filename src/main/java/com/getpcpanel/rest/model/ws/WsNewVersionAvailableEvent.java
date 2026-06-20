package com.getpcpanel.rest.model.ws;

import com.fasterxml.jackson.annotation.JsonTypeName;

@JsonTypeName("new_version_available")
public record WsNewVersionAvailableEvent(String version, String url) implements WsEvent {
}
