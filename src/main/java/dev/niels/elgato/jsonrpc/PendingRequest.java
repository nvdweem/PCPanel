package dev.niels.elgato.jsonrpc;

import java.util.concurrent.CompletableFuture;

record PendingRequest<T>(Class<T> resultClass, CompletableFuture<T> future) {
}
