package com.getpcpanel.integration.discord;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.getpcpanel.util.SharedHttpClient;

/**
 * The OAuth2 token half of the Discord authorization, kept out of the {@code dev.niels.discord} library
 * because it needs the client secret. The IPC AUTHORIZE step returns a code over the pipe; this exchanges
 * it (and later refreshes it) at Discord's token endpoint via the shared {@link SharedHttpClient}.
 */
final class DiscordOAuth {
    private static final String TOKEN_URL = "https://discord.com/api/oauth2/token";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private DiscordOAuth() {
    }

    record TokenResponse(String accessToken, @Nullable String refreshToken, long expiresInSeconds, @Nullable String scope) {
    }

    static CompletableFuture<TokenResponse> exchangeCode(String clientId, String clientSecret, String redirectUri, String code) {
        return post(Map.of(
                "grant_type", "authorization_code",
                "code", code,
                "redirect_uri", redirectUri,
                "client_id", clientId,
                "client_secret", clientSecret));
    }

    static CompletableFuture<TokenResponse> refresh(String clientId, String clientSecret, String refreshToken) {
        return post(Map.of(
                "grant_type", "refresh_token",
                "refresh_token", refreshToken,
                "client_id", clientId,
                "client_secret", clientSecret));
    }

    private static CompletableFuture<TokenResponse> post(Map<String, String> form) {
        var body = form.entrySet().stream()
                       .map(e -> enc(e.getKey()) + '=' + enc(e.getValue()))
                       .collect(Collectors.joining("&"));
        var request = HttpRequest.newBuilder(URI.create(TOKEN_URL))
                                 .header("Content-Type", "application/x-www-form-urlencoded")
                                 .timeout(Duration.ofSeconds(15))
                                 .POST(HttpRequest.BodyPublishers.ofString(body))
                                 .build();
        return SharedHttpClient.get().sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenApply(DiscordOAuth::parse);
    }

    private static TokenResponse parse(HttpResponse<String> response) {
        JsonNode node;
        try {
            node = MAPPER.readTree(response.body());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to parse Discord token response", e);
        }
        if (response.statusCode() / 100 != 2 || !node.hasNonNull("access_token")) {
            var error = node.path("error_description").asText(node.path("error").asText("HTTP " + response.statusCode()));
            throw new IllegalStateException("Discord token request failed: " + error);
        }
        return new TokenResponse(
                node.get("access_token").asText(),
                node.path("refresh_token").asText(null),
                node.path("expires_in").asLong(0),
                node.path("scope").asText(null));
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
