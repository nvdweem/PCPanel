package dev.niels.elgato.jsonrpc;

import java.net.http.WebSocket;
import java.net.http.WebSocket.Listener;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import org.springframework.util.ReflectionUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;

@Log4j2
@RequiredArgsConstructor
class JsonRpcWebSocketListener implements Listener {
    private final JsonRpcClient client;
    private final JsonRpcService service;
    private final Map<String, Consumer<JsonNode>> serviceMethodCache = new ConcurrentHashMap<>();
    private final StringBuffer msgBuffer = new StringBuffer();

    @Override
    public void onOpen(WebSocket webSocket) {
        Listener.super.onOpen(webSocket);
        log.debug("WebSocket opened");
        client.websocket = CompletableFuture.completedFuture(webSocket);
        CompletableFuture.runAsync(() -> service._onConnect(client));
        client.rpcListener.connected();
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
        msgBuffer.append(data);
        if (last) {
            var fullMessage = msgBuffer.toString();
            msgBuffer.setLength(0);
            log.debug("Received message: {}", fullMessage);
            CompletableFuture.runAsync(() -> {
                try {
                    handleFullMessage(fullMessage);
                } catch (JsonProcessingException e) {
                    log.error("Failed to parse JSON-RPC message: {}", fullMessage, e);
                }
            });
        }
        return Listener.super.onText(webSocket, data, last);
    }

    private void handleFullMessage(String fullMessage) throws JsonProcessingException {
        var tree = client.mapper.readTree(fullMessage);
        if (tree.has("method")) {
            handleMethod(tree);
        } else if (tree.has("result")) {
            getRequest(tree).ifPresent(req -> handleResult(tree, req));
        } else if (tree.has("error")) {
            getRequest(tree).ifPresent(req -> handleError(tree, req));
        }
    }

    @SneakyThrows
    private void handleResult(JsonNode tree, PendingRequest<Object> request) {
        if (Void.class.equals(request.resultClass().getRawClass())) {
            request.future().complete(null);
        } else {
            var resultNode = tree.get("result");
            var resultValue = client.mapper.treeToValue(resultNode, request.resultClass());
            request.future().complete(resultValue);
        }
    }

    @SneakyThrows
    private void handleError(JsonNode tree, PendingRequest<Object> req) {
        var ex = client.mapper.treeToValue(tree.get("error"), JsonRpcException.class);
        req.future().completeExceptionally(ex);
        client.rpcListener.onError(ex);
    }

    private <T> Optional<PendingRequest<T>> getRequest(JsonNode tree) {
        var id = tree.get("id").longValue();
        return Optional.ofNullable((PendingRequest<T>) client.pendingRequests.get(id));
    }

    private void handleMethod(JsonNode tree) {
        var methodName = tree.get("method").asText();
        serviceMethodCache.computeIfAbsent(methodName, this::buildParser).accept(tree.get("params"));
    }

    private Consumer<JsonNode> buildParser(String methodName) {
        var serviceMethod = ReflectionUtils.findMethod(service.getClass(), methodName, null);
        if (serviceMethod == null) {
            log.warn("No method '{}' found on service {}", methodName, service.getClass().getSimpleName());
            return s -> log.debug("Not handling method call {}: {}", methodName, s);
        }
        serviceMethod.setAccessible(true);

        return paramsNode -> {
            try {
                log.debug("Invoking service method '{}'", methodName);
                if (serviceMethod.getParameterCount() == 0) {
                    serviceMethod.invoke(service);
                } else if (serviceMethod.getParameterCount() == 1 && paramsNode != null && !paramsNode.isNull()) {
                    var arg = client.mapper.treeToValue(paramsNode, serviceMethod.getParameterTypes()[0]);
                    serviceMethod.invoke(service, arg);
                }
            } catch (ReflectiveOperationException e) {
                log.error("Failed to invoke method '{}' on service", methodName, e);
            } catch (JsonProcessingException e) {
                log.error("Failed to parse {} to {}", paramsNode, serviceMethod.getParameterTypes()[0], e);
            }
        };
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
        log.info("WebSocket closed with status code {} and reason {}", statusCode, reason);
        CompletableFuture.runAsync(service::_onClose);
        try {
            return Listener.super.onClose(webSocket, statusCode, reason);
        } finally {
            client.rpcListener.connectionClosed();
        }
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
        log.error("WebSocket error", error);
        CompletableFuture.runAsync(() -> service._onError(error));
        Listener.super.onError(webSocket, error);
        client.rpcListener.onError(error);
    }
}
