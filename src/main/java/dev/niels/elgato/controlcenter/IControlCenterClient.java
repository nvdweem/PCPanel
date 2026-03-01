package dev.niels.elgato.controlcenter;

import java.util.concurrent.CompletableFuture;

public interface IControlCenterClient {
    boolean isInitialized();

    boolean isConnected();

    void ping();

    CompletableFuture<Void> reconnect();

    CompletableFuture<Void> disconnect();
}
