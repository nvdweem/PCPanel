package com.getpcpanel.homeassistant.dto;

import javax.annotation.Nullable;

/**
 * A single configured Home Assistant connection. {@code id} is a stable identifier referenced by
 * the Home Assistant commands; {@code name} is the user-facing label; {@code url} is the base URL
 * (e.g. {@code http://homeassistant.local:8123}); {@code token} is a long-lived access token.
 */
public record HomeAssistantServer(String id, String name, String url, @Nullable String token) {
}
