package dev.niels.wavelink.impl;

import java.net.http.WebSocket;
import java.net.http.WebSocket.Listener;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.niels.wavelink.IWaveLinkClientEventListener;
import dev.niels.wavelink.impl.rpc.JsonRpcMessage;
import dev.niels.wavelink.impl.rpc.JsonRpcResponse;
import dev.niels.wavelink.impl.rpc.WaveLinkGetApplicationInfo;
import dev.niels.wavelink.impl.rpc.WaveLinkGetApplicationInfo.WaveLinkGetApplicationInfoResult;
import dev.niels.wavelink.impl.rpc.WaveLinkGetChannels;
import dev.niels.wavelink.impl.rpc.WaveLinkGetInputDevices;
import dev.niels.wavelink.impl.rpc.WaveLinkGetMixes;
import dev.niels.wavelink.impl.rpc.WaveLinkGetOutputDevices;
import dev.niels.wavelink.impl.rpc.WaveLinkJsonRpcCommand;
import dev.niels.wavelink.impl.rpc.WaveLinkSetSubscription;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;

@Log4j2
@RequiredArgsConstructor
public class WaveLinkListener implements Listener {
    private final ObjectMapper mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private final Map<Long, PendingRequest> pendingRequests = new ConcurrentHashMap<>();
    private final WaveLinkClientImpl client;
    @Nullable private WebSocket socket;
    private long nextSendId;
    private final StringBuffer msgBuffer = new StringBuffer();

    @Override
    public void onOpen(WebSocket webSocket) {
        socket = webSocket;
        log.debug("WebSocket opened");
        Listener.super.onOpen(webSocket);
        client.trigger(IWaveLinkClientEventListener::connected);

        log.trace("Sending get info request");
        sendExpectingResult(new WaveLinkGetApplicationInfo()).thenAccept(res -> {
            ensureCorrectVersion(res);
            log.debug("Connected to Wave Link, getting info");
            getInfo();
        });
    }

    private void getInfo() {
        CompletableFuture.allOf(
                sendExpectingResult(new WaveLinkGetInputDevices()).thenAccept(res -> client.updateInputDevices(res.inputDevices())),
                sendExpectingResult(new WaveLinkGetOutputDevices()).thenAccept(res -> client.updateOutputDevices(res.outputDevices(), res.mainOutput())),
                sendExpectingResult(new WaveLinkGetChannels()).thenAccept(res -> client.updateChannels(res.channels())),
                sendExpectingResult(new WaveLinkGetMixes()).thenAccept(res -> client.updateMixes(res.mixes())),
                sendExpectingResult(WaveLinkSetSubscription.setFocusAppChanged(true)).thenAccept(res -> {
                    log.debug("Successfully subscribed to websocket events");
                })
        ).thenRun(client::setInitialized);
    }

    private void ensureCorrectVersion(WaveLinkGetApplicationInfoResult res) {
        log.info("Connected websocket, wavelink info: {}", res);
        var correctAppId = "ewl".equalsIgnoreCase(res.appID());
        var correctAppName = "Elgato Wave Link".equalsIgnoreCase(res.name());
        if (!correctAppId || !correctAppName) {
            throw new IllegalStateException("Expected appId ewl and appName Elgato Wave Link, got " + res);
        }
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
        msgBuffer.append(data);
        if (last) {
            var fullMessage = msgBuffer.toString();
            msgBuffer.setLength(0);
            log.debug("Received message: {}", fullMessage);
            try {
                handleMessage(mapper.readValue(fullMessage, JsonRpcMessage.class));
            } catch (JsonProcessingException e) {
                log.error("Failed to parse JSON-RPC message: {}", fullMessage, e);
            }
        }
        return Listener.super.onText(webSocket, data, last);
    }

    private void handleMessage(JsonRpcMessage message) {
        switch (message) {
            case JsonRpcResponse response -> handleResponse(response);
            case WaveLinkJsonRpcCommand<?, ?> command -> handleCommand(command);
        }
    }

    private void handleResponse(JsonRpcResponse response) {
        var id = response.getId();
        if (id == null) {
            log.warn("Received response without ID, ignoring");
            return;
        }
        var pending = pendingRequests.remove(id);

        if (pending == null) {
            log.warn("Received response for unknown request ID {}", id);
            return;
        }

        if (response.getError() != null) {
            var error = response.getError();
            var errorMessage = String.format("JSON-RPC error %d: %s%s",
                    error.getCode(),
                    error.getMessage(),
                    error.getData() != null ? " [" + error.getData() + "]" : "");
            log.error("Received error for request ID {}: {}", id, errorMessage);
            pending.future.completeExceptionally(new RuntimeException(errorMessage));
            return;
        }

        try {
            var value = mapper.treeToValue(response.getResult(), pending.resultClass);
            ((CompletableFuture<Object>) pending.future).complete(value);
            log.debug("Successfully handled result for request ID {}", id);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse result for request ID {} as {}", id, pending.resultClass.getSimpleName(), e);
            pending.future.completeExceptionally(new RuntimeException("Failed to parse JSON-RPC result", e));
        }
    }

    private void handleCommand(WaveLinkJsonRpcCommand<?, ?> command) {
        log.debug("Received command: {}", command.getClass().getSimpleName());
        client.onCommand(command);
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
        socket = null;
        log.info("WebSocket closed with status code {} and reason {}", statusCode, reason);
        client.trigger(IWaveLinkClientEventListener::connectionClosed);
        return Listener.super.onClose(webSocket, statusCode, reason);
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
        log.error("WebSocket error", error);
        client.trigger(l -> l.onError(error));
        Listener.super.onError(webSocket, error);
    }

    @SneakyThrows
    public <R> CompletableFuture<R> sendExpectingResult(WaveLinkJsonRpcCommand<?, R> message) {
        var socket = ensureSocketNotClosed();

        var result = new CompletableFuture<R>();
        synchronized (pendingRequests) {
            message.setId(nextSendId++);
            pendingRequests.put(message.getId(), new PendingRequest(message.getResultClass(), result));
        }
        var messageText = mapper.writeValueAsString(message);
        log.debug("Sending: {}", messageText);
        socket.sendText(messageText, true);

        return result;
    }

    @Nonnull
    private WebSocket ensureSocketNotClosed() {
        if (socket == null || socket.isOutputClosed() || socket.isInputClosed()) {
            throw new IllegalStateException("WebSocket is closed");
        }
        return socket;
    }

    record PendingRequest(Class<?> resultClass, CompletableFuture<?> future) {
    }
}
