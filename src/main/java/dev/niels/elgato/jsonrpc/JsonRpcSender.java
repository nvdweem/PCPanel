package dev.niels.elgato.jsonrpc;

import java.util.concurrent.CompletableFuture;

public interface JsonRpcSender {
    <R> CompletableFuture<R> sendExpectingResult(JsonRpcCommand<?> message, Class<R> resultClass);

    default <R> CompletableFuture<R> sendExpectingResult(String message, Class<R> resultClass) {
        return sendExpectingResult(new JsonRpcCommand<>(message, null), resultClass);
    }

    default <R> CompletableFuture<R> sendExpectingResult(String message, Object params, Class<R> resultClass) {
        return sendExpectingResult(new JsonRpcCommand<>(message, params), resultClass);
    }
}
