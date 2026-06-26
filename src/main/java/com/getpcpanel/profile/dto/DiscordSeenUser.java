package com.getpcpanel.profile.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A Discord user the app has seen in a voice channel, persisted so the command editor can offer a
 * username to target even when not currently in a call. Commands match on {@code username} (the stable,
 * user-facing handle); {@code id} is kept to address the user over RPC; {@code displayName} is for the UI.
 */
public record DiscordSeenUser(String id, String username, String displayName) {
    @JsonCreator
    public DiscordSeenUser(
            @JsonProperty("id") String id,
            @JsonProperty("username") String username,
            @JsonProperty("displayName") String displayName) {
        this.id = id;
        this.username = username;
        this.displayName = displayName;
    }
}
