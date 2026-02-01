package dev.niels.wavelink.impl;

import java.net.http.WebSocket;
import java.net.http.WebSocket.Listener;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;

import dev.niels.wavelink.impl.rpc.WaveLinkGetApplicationInfo;
import dev.niels.wavelink.impl.rpc.WaveLinkGetApplicationInfo.WaveLinkGetApplicationInfoResult;
import dev.niels.wavelink.impl.rpc.WaveLinkGetChannels;
import dev.niels.wavelink.impl.rpc.WaveLinkGetInputDevices;
import dev.niels.wavelink.impl.rpc.WaveLinkGetMixes;
import dev.niels.wavelink.impl.rpc.WaveLinkGetOutputDevices;
import dev.niels.wavelink.impl.rpc.WaveLinkJsonRpcCommand;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;

@Log4j2
@RequiredArgsConstructor
public class WaveLinkListener implements Listener {
    private final ObjectMapper mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private final ObjectReader messageReader = mapper.readerFor(WaveLinkJsonRpcCommand.class);
    private final Map<Long, PendingRequest> pendingRequests = new ConcurrentHashMap<>();
    private final WaveLinkClientImpl client;
    @Nullable private WebSocket socket;
    private long nextSendId;

    @Override
    public void onOpen(WebSocket webSocket) {
        socket = webSocket;
        log.debug("WebSocket opened");
        Listener.super.onOpen(webSocket);

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
                sendExpectingResult(new WaveLinkGetMixes()).thenAccept(res -> client.updateMixes(res.mixes()))
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
        log.info("Received message: {}", data);
        try {
            var tree = mapper.readTree(data.toString());
            if (!tryReadResult(tree)) {
                readMessage(messageReader.readValue(data.toString()));
            }
        } catch (JsonProcessingException e) {
            log.error("Failed to parse message, {}", data, e);
        }
        return Listener.super.onText(webSocket, data, last);
    }

    private boolean tryReadResult(JsonNode tree) {
        if (!tree.has("result")) {
            return false;
        }
        var id = tree.get("id").asLong();
        var pending = pendingRequests.get(id);
        if (pending == null) {
            log.warn("Received result for unknown request {}: {}", id, tree.toPrettyString());
            return true;
        }

        try {
            var result = mapper.treeToValue(tree.get("result"), pending.resultClass);
            ((CompletableFuture<Object>) pending.future).complete(result);
        } catch (JsonProcessingException e) {
            log.warn("Unable to read {} from {}", pending.resultClass, tree.toPrettyString(), e);
            pending.future.complete(null);
        }
        return true;
    }

    private void readMessage(WaveLinkJsonRpcCommand<?, ?> cmd) {
        client.onMessage(cmd);
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
        socket = null;
        log.info("WebSocket closed with status code {} and reason {}", statusCode, reason);
        return Listener.super.onClose(webSocket, statusCode, reason);
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
        log.error("WebSocket error", error);
        Listener.super.onError(webSocket, error);
    }

    @SneakyThrows
    public <R> CompletableFuture<R> sendExpectingResult(WaveLinkJsonRpcCommand<?, R> message) {
        ensureSocketNotClosed();

        var result = new CompletableFuture<R>();
        synchronized (pendingRequests) {
            message.setId(nextSendId++);
            pendingRequests.put(message.getId(), new PendingRequest(message.getResultClass(), result));
        }
        var messageText = mapper.writeValueAsString(message);
        log.info("Sending: {}", messageText);
        socket.sendText(messageText, true);

        return result;
    }

    private void ensureSocketNotClosed() {
        if (socket == null || socket.isOutputClosed() || socket.isInputClosed()) {
            throw new IllegalStateException("WebSocket is closed");
        }
    }

    record PendingRequest(Class<?> resultClass, CompletableFuture<?> future) {
    }
}
