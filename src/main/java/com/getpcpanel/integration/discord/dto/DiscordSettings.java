package com.getpcpanel.integration.discord.dto;

import org.apache.commons.lang3.StringUtils;

import javax.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * User-editable Discord integration config. Voice control needs the {@code rpc.voice.write} OAuth scope,
 * which Discord only grants to the application owner — so each user registers their own (free) Discord
 * application and supplies its {@code clientId}/{@code clientSecret}. {@code redirectUri} must match one
 * registered on that application (default {@code http://localhost}); it is only used in the OAuth code
 * exchange, never opened in a browser (the code comes back over IPC). The obtained tokens live separately
 * in {@code Save.discordAuth} so saving config never clobbers them.
 *
 * @param enabled      Discord integration on/off.
 * @param clientId     the user's Discord application client id.
 * @param clientSecret the user's Discord application client secret.
 * @param redirectUri  an OAuth2 redirect URI registered on the application; default {@code http://localhost}.
 */
public record DiscordSettings(boolean enabled, @Nullable String clientId, @Nullable String clientSecret, String redirectUri) {
    public static final String DEFAULT_REDIRECT_URI = "http://localhost";
    public static final DiscordSettings DEFAULT = new DiscordSettings(false, null, null, DEFAULT_REDIRECT_URI);

    @JsonCreator
    public DiscordSettings(
            @JsonProperty("enabled") boolean enabled,
            @JsonProperty("clientId") @Nullable String clientId,
            @JsonProperty("clientSecret") @Nullable String clientSecret,
            @JsonProperty("redirectUri") @Nullable String redirectUri) {
        this.enabled = enabled;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUri = StringUtils.isBlank(redirectUri) ? DEFAULT_REDIRECT_URI : redirectUri;
    }
}
