package dev.niels.discord;

import com.fasterxml.jackson.databind.JsonNode;

/** A Discord RPC error frame ({@code {"code":..,"message":..}}), surfaced as an exception on the failing request. */
public class DiscordRpcException extends RuntimeException {
    private final int code;

    public DiscordRpcException(int code, String message) {
        super(message);
        this.code = code;
    }

    public DiscordRpcException(JsonNode data) {
        this(data == null ? -1 : data.path("code").asInt(-1),
                data == null ? "Unknown error" : data.path("message").asText("Unknown error"));
    }

    public int getCode() {
        return code;
    }
}
