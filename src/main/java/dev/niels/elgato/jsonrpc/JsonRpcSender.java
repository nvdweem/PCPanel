package dev.niels.elgato.jsonrpc;

import java.lang.reflect.Type;
import java.util.concurrent.CompletableFuture;

import javax.annotation.Nullable;

import com.fasterxml.jackson.databind.JavaType;

import lombok.With;

public interface JsonRpcSender {
    <R> CompletableFuture<R> sendExpectingResult(JsonRpcCommand<?> message, JavaType resultClass);

    default <R> CompletableFuture<R> sendExpectingResult(String message, Class<R> resultClass) {
        return sendExpectingResult(new JsonRpcCommand<>(message, null), toJavaType(resultClass));
    }

    default <R> CompletableFuture<R> sendExpectingResult(String message, Object params, Class<R> resultClass) {
        return sendExpectingResult(new JsonRpcCommand<>(message, params), toJavaType(resultClass));
    }

    JavaType toJavaType(Type type);

    record JsonRpcCommand<T>(@With long id, String method, T params, String jsonrpc) {
        public JsonRpcCommand(String method, @Nullable T params) {
            this(0, method, params, "2.0");
        }
    }
}
