package dev.niels.elgato.jsonrpc;

public interface IJsonRpcClientListener {
    default void connected() {
    }

    default void connectionClosed() {
    }

    default void onError(Throwable t) {
    }
}
