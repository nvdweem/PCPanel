package dev.niels.elgato.shared;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import dev.niels.elgato.jsonrpc.JsonRpcClient;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;

@Log4j2
public abstract class ClientImpl<L extends IRpcListener> implements IRpcListener {
    private final List<L> listeners = new CopyOnWriteArrayList<>();
    @Getter protected boolean initialized;

    protected abstract JsonRpcClient getJsonRpc();

    public boolean isConnected() {
        return getJsonRpc().isConnected();
    }

    public void ping() {
        getJsonRpc().ping();
    }

    public CompletableFuture<Void> reconnect() {
        return getJsonRpc().reconnect();
    }

    public CompletableFuture<Void> disconnect() {
        return getJsonRpc().disconnect();
    }

    public void addListener(L listener) {
        listeners.add(listener);
    }

    public void removeListener(L listener) {
        listeners.remove(listener);
    }

    public void close() {
        getJsonRpc().close();
    }

    public void trigger(Consumer<L> event) {
        listeners.forEach(event);
    }

    public void setInitialized() {
        log.info("Connected to Wave Link and initialized");
        initialized = true;
        trigger(IRpcListener::initialized);
    }
}
