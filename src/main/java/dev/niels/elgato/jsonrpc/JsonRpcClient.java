package dev.niels.elgato.jsonrpc;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class JsonRpcClient implements AutoCloseable {
    private final HttpClient client;
    private final String uri;
    private final JsonRpcListener jsonRpcListener;
    private CompletableFuture<WebSocket> websocket = CompletableFuture.completedFuture(null);

    public JsonRpcClient(String uri, boolean autoConnect, Object service) {
        this.uri = uri;
        jsonRpcListener = new JsonRpcListener(service);
        client = HttpClient.newHttpClient();
        if (autoConnect) {
            connect();
        }
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
                          .buildAsync(URI.create(uri), jsonRpcListener)
                          .exceptionally(ex -> {
                              // trigger(l -> l.onError(ex));
                              return null;
                          });
        return websocket;
    }

    public CompletableFuture<Void> reconnect() {
        return disconnect()
                .thenAccept(x -> connect())
                .exceptionallyCompose(ex -> connect().thenAccept(x -> log.error("Connect after error", ex)));
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
