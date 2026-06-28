package com.getpcpanel.integration.homeassistant.dto;

import javax.annotation.Nullable;

/**
 * Connection state of a configured Home Assistant server, reported to the UI. The {@code url} lets
 * the action editor link out to that server's Developer Tools → Actions page.
 */
public record HomeAssistantServerStatus(String id, String name, @Nullable String url, boolean connected) {
}
