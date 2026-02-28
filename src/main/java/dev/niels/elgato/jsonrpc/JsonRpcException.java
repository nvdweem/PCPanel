package dev.niels.elgato.jsonrpc;

import javax.annotation.Nullable;

import com.fasterxml.jackson.databind.JsonNode;

import lombok.Data;

@Data
public class JsonRpcException extends Exception {
    private final long code;
    private final String message;
    @Nullable private final JsonNode data;
}
