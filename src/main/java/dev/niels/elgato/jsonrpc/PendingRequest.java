package dev.niels.elgato.jsonrpc;

import java.util.concurrent.CompletableFuture;

import com.fasterxml.jackson.databind.JavaType;

record PendingRequest<T>(JavaType resultClass, CompletableFuture<T> future) {
}
