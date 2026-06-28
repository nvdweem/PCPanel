package com.getpcpanel.integration.discord;

/** Fired on the CDI event bus whenever Discord connection/auth state or voice state changes, so the UI re-reads it. */
public record DiscordChangedEvent() {
}
