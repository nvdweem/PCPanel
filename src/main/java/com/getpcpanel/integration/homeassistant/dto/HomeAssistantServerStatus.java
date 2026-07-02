package com.getpcpanel.integration.homeassistant.dto;

import javax.annotation.Nullable;

/**
 * Connection state of a configured Home Assistant server, reported to the UI. The {@code url} lets
 * the action editor link out to that server's Developer Tools → Actions page. {@code warning} is a
 * user-facing configuration warning (currently: plain HTTP to a non-local host), null when there is
 * nothing to flag.
 */
public record HomeAssistantServerStatus(String id, String name, @Nullable String url, boolean connected, @Nullable String warning) {
}
