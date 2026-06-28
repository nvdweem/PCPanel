package com.getpcpanel.integration.discord.dto;

import javax.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Machine-managed Discord OAuth state, persisted so the user only has to authorize once. Kept separate
 * from {@link DiscordSettings} (user config) so a settings save never overwrites the tokens. Refreshed
 * automatically via the refresh token before expiry.
 */
public record DiscordAuth(@Nullable String accessToken, @Nullable String refreshToken, @Nullable Long expiresAtEpochMs,
        @Nullable String scope, @Nullable String userId, @Nullable String userName) {
    @JsonCreator
    public DiscordAuth(
            @JsonProperty("accessToken") @Nullable String accessToken,
            @JsonProperty("refreshToken") @Nullable String refreshToken,
            @JsonProperty("expiresAtEpochMs") @Nullable Long expiresAtEpochMs,
            @JsonProperty("scope") @Nullable String scope,
            @JsonProperty("userId") @Nullable String userId,
            @JsonProperty("userName") @Nullable String userName) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.expiresAtEpochMs = expiresAtEpochMs;
        this.scope = scope;
        this.userId = userId;
        this.userName = userName;
    }

    @JsonIgnore
    public boolean hasToken() {
        return accessToken != null && !accessToken.isBlank();
    }
}
