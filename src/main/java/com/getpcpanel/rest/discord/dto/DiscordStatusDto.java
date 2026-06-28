package com.getpcpanel.rest.discord.dto;

import javax.annotation.Nullable;

import com.getpcpanel.integration.discord.DiscordService;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Live Discord integration status for the settings page and command picker.
 *
 * @param enabled       integration is enabled and has client id + secret.
 * @param connected     the IPC pipe/socket is open (Discord is running and reachable).
 * @param authenticated the OAuth handshake completed — voice commands work.
 * @param authorized    a token is stored (the user authorized at least once), even if not currently connected.
 * @param user          the authenticated/last-authorized user's display name.
 * @param lastError     why the last authorize attempt failed (null once authorized), for the UI to show.
 */
@RegisterForReflection
public record DiscordStatusDto(boolean enabled, boolean connected, boolean authenticated, boolean authorized,
        @Nullable String user, @Nullable String lastError) {
    public static DiscordStatusDto from(DiscordService service) {
        return new DiscordStatusDto(
                service.isEnabled(),
                service.isConnected(),
                service.isAuthenticated(),
                service.hasStoredAuthorization(),
                service.getStatusUserName(),
                service.getLastAuthError());
    }
}
