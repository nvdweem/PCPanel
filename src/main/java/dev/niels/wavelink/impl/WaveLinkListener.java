package dev.niels.wavelink.impl;

import java.net.http.WebSocket;
import java.net.http.WebSocket.Listener;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

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
    private static final long REQUEST_TIMEOUT_MS = 10_000;
    private final ObjectMapper mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private final Map<Long, PendingRequest> pendingRequests = new ConcurrentHashMap<>();
    private final WaveLinkClientImpl client;
    @Nullable private WebSocket socket;
    private long nextSendId;
    private final StringBuffer msgBuffer = new StringBuffer();
    /** Guards {@link #writeChain} so concurrent senders queue behind each other rather than race. */
    private final Object writeLock = new Object();
    /** Completes when the last queued write is on the wire; the next write is chained onto it. */
    private CompletableFuture<?> writeChain = CompletableFuture.completedFuture(null);

    @Override
    public void onOpen(WebSocket webSocket) {
        socket = webSocket;
        client.markConnected(System.currentTimeMillis());
        log.debug("WebSocket opened");
        Listener.super.onOpen(webSocket);
        client.trigger(IWaveLinkClientEventListener::connected);

        log.trace("Sending get info request");
        sendExpectingResult(new WaveLinkGetApplicationInfo()).thenAccept(res -> {
            ensureCorrectVersion(res);
            log.debug("Connected to Wave Link, getting info");
            getInfo();
        }).exceptionally(ex -> {
            // Nothing retries this handshake, so the connection stays open and uninitialised until the
            // health check reconnects it. Log why rather than leaving the reconnect loop unexplained.
            log.error("Wave Link handshake failed at getApplicationInfo", ex);
            return null;
        });
    }

    private void getInfo() {
        CompletableFuture.allOf(sendExpectingResult(new WaveLinkGetInputDevices()).thenAccept(res -> client.updateInputDevices(res.inputDevices())),
                sendExpectingResult(new WaveLinkGetOutputDevices()).thenAccept(res -> client.updateOutputDevices(res.outputDevices(), res.mainOutput())),
                sendExpectingResult(new WaveLinkGetChannels()).thenAccept(res -> client.updateChannels(res.channels())), sendExpectingResult(new WaveLinkGetMixes()).thenAccept(res -> client.updateMixes(res.mixes())),
                sendExpectingResult(WaveLinkSetSubscription.setFocusAppChanged(true)).thenAccept(res -> {
                    log.debug("Successfully subscribed to websocket events");
                })).whenComplete((ignored, ex) -> {
                    if (ex == null) {
                        client.setInitialized();
                    } else {
                        // An unanswered request leaves the client uninitialised — open but useless — so
                        // name the failure instead of letting the health check reconnect in silence.
                        log.error("Wave Link handshake did not complete; connection stays uninitialised", ex);
                    }
                });
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
        client.recordInboundActivity(System.currentTimeMillis());
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
        if (message instanceof JsonRpcResponse response) {
            handleResponse(response);
        } else if (message instanceof WaveLinkJsonRpcCommand<?, ?> command) {
            handleCommand(command);
        }
    }

    private void handleResponse(JsonRpcResponse response) {
        var id = response.getId();
        if (id == null) {
            log.debug("Received response without ID, ignoring");
            return;
        }
        var pending = pendingRequests.remove(id);

        if (pending == null) {
            log.warn("Received response for unknown request ID {}", id);
            return;
        }

        if (response.getError() != null) {
            var error = response.getError();
            var errorMessage = String.format("JSON-RPC error %d: %s%s", error.getCode(), error.getMessage(), error.getData() != null ? " [" + error.getData() + "]" : "");
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
    public CompletionStage<?> onPong(WebSocket webSocket, ByteBuffer message) {
        // Wave Link's reply to our keepalive ping: proof the peer is still alive on an otherwise idle
        // connection, so the inactivity window (and thus the reconnect decision) resets.
        client.recordInboundActivity(System.currentTimeMillis());
        return Listener.super.onPong(webSocket, message);
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
        socket = null;
        client.markDisconnected();
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
            pendingRequests.put(message.getId(), new PendingRequest(message, message.getResultClass(), result));
        }
        // Time out the request if Wave Link never answers, and always drop the pending entry when it
        // settles, so the pendingRequests map cannot grow without bound on a stalled connection. A
        // timeout is named in the log because it is the one thing that distinguishes a Wave Link that
        // accepted the request and never answered from one we failed to send to at all — the two
        // otherwise leave identical traces: a handshake that simply never finishes.
        var requestId = message.getId();
        var requestName = message.getClass().getSimpleName();
        result.orTimeout(REQUEST_TIMEOUT_MS, TimeUnit.MILLISECONDS)
              .whenComplete((r, ex) -> {
                  pendingRequests.remove(requestId);
                  if (ex instanceof TimeoutException) {
                      log.warn("Wave Link did not answer request {} ({}) within {}ms", requestId, requestName, REQUEST_TIMEOUT_MS);
                  }
              });
        var messageText = mapper.writeValueAsString(message);
        log.debug("Sending: {}", messageText);
        queueWrite(socket, messageText).exceptionally(ex -> {
            log.error("Failed to send request {} ({})", requestId, requestName, ex);
            result.completeExceptionally(ex);
            return null;
        });

        return result;
    }

    /**
     * Puts one message on the wire once the previous one is there. {@link WebSocket#sendText} accepts a
     * single write at a time and rejects any other with {@code IllegalStateException("Send pending")}
     * without sending it, so messages issued in a burst — the post-connect handshake, or the run of
     * commands a turning dial produces — are chained instead of fired off together. Each write is
     * chained on the previous one's <em>outcome</em>, so one failure delays nothing but itself.
     */
    private CompletableFuture<?> queueWrite(WebSocket socket, String messageText) {
        synchronized (writeLock) {
            var write = writeChain.handle((ignored, ex) -> null)
                                  .thenCompose(ignored -> socket.sendText(messageText, true));
            writeChain = write;
            return write;
        }
    }

    @Nonnull
    private WebSocket ensureSocketNotClosed() {
        if (socket == null || socket.isOutputClosed() || socket.isInputClosed()) {
            throw new IllegalStateException("WebSocket is closed");
        }
        return socket;
    }

    record PendingRequest(WaveLinkJsonRpcCommand<?, ?> request, Class<?> resultClass, CompletableFuture<?> future) {
    }
}
