package dev.niels.elgato.jsonrpc;

import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class JsonRpcClient implements AutoCloseable, JsonRpcSender {
    final ObjectMapper mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private final HttpClient client;
    private final String uri;
    final IJsonRpcClientListener rpcListener;
    private final JsonRpcWebSocketListener jsonRpcWebSocketListener;
    CompletableFuture<WebSocket> websocket = CompletableFuture.completedFuture(null);
    final Map<Long, PendingRequest<?>> pendingRequests = new ConcurrentHashMap<>();
    private long nextSendId;

    public JsonRpcClient(String uri, boolean autoConnect, IJsonRpcClientListener rpcListener, JsonRpcService service) {
        this.uri = uri;
        this.rpcListener = rpcListener;
        jsonRpcWebSocketListener = new JsonRpcWebSocketListener(this, service);
        client = HttpClient.newHttpClient();
        if (autoConnect) {
            connect();
        }
    }

    @Override
    public JavaType toJavaType(Type type) {
        return mapper.constructType(type);
    }

    public <T> T buildCommands(Class<T> commandsInterface) {
        return JsonRpcWrapper.wrap(commandsInterface, this);
    }

    public boolean isConnected() {
        var ws = websocket.getNow(null);
        if (ws == null) {
            return false;
        }
        return !ws.isInputClosed() && !ws.isOutputClosed();
    }

    public void ping() {
        Optional.ofNullable(websocket.getNow(null))
                .ifPresent(ws ->
                        ws.sendPing(ByteBuffer.wrap("ping".getBytes())).exceptionally(ex -> {
                            if (ex instanceof IOException) {
                                ensureDisconnect();
                            }
                            return null;
                        })
                );
    }

    public CompletableFuture<WebSocket> connect() {
        ensureDisconnect();
        log.debug("Connecting");
        websocket = client.newWebSocketBuilder()
                          .header("Origin", "streamdeck://")
                          .buildAsync(URI.create(uri), jsonRpcWebSocketListener)
                          .exceptionally(ex -> {
                              rpcListener.onError(ex);
                              return null;
                          });
        return websocket;
    }

    public CompletableFuture<Void> reconnect() {
        return disconnect()
                .thenAccept(x -> connect())
                .exceptionallyCompose(ex -> connect().thenAccept(x -> log.error("Connect after error", ex)));
    }

    @Override
    @SneakyThrows
    public <R> CompletableFuture<R> sendExpectingResult(JsonRpcCommand<?> message, JavaType resultClass) {
        var socket = ensureSocketNotClosed();

        var result = new CompletableFuture<R>();
        var sending = message;
        synchronized (pendingRequests) {
            nextSendId++;
            sending = message.withId(nextSendId);
            pendingRequests.put(sending.id(), new PendingRequest<>(resultClass, result));
        }

        var sendingText = mapper.writeValueAsString(sending);
        log.debug("Sending: {}", sendingText);
        socket.sendText(sendingText, true);
        return result;
    }

    @Nonnull
    private WebSocket ensureSocketNotClosed() {
        var socket = websocket.getNow(null);
        if (socket == null || socket.isOutputClosed() || socket.isInputClosed()) {
            throw new IllegalStateException("WebSocket is closed");
        }
        return socket;
    }

    public CompletableFuture<Void> disconnect() {
        if (isConnected()) {
            var wsa = new WebSocket[1];
            return websocket
                    .thenCompose(
                            ws -> {
                                wsa[0] = ws;
                                return ws == null
                                        ? CompletableFuture.completedFuture(null)
                                        : ws.sendClose(WebSocket.NORMAL_CLOSURE, "Reconnecting").thenAccept(x -> {
                                });
                            }
                    )
                    .exceptionally(ex -> {
                        log.warn("Error disconnecting websocket", ex);
                        if (wsa[0] != null) {
                            wsa[0].abort();
                        }
                        return null;
                    });
        }
        return CompletableFuture.completedFuture(null);
    }

    private void ensureDisconnect() {
        var currentWs = websocket.getNow(null);
        if (currentWs != null) {
            currentWs.abort();
        }
        websocket = CompletableFuture.completedFuture(null);
    }

    @Override
    public void close() {
        var socket = websocket.join();
        if (socket != null && (!socket.isInputClosed() || !socket.isOutputClosed())) {
            socket.sendClose(WebSocket.NORMAL_CLOSURE, "Done");
        }
        try {
            client.close();
        } catch (Exception e) {
            log.error("Error closing websocket", e);
        }
    }
}
