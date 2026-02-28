package dev.niels.elgato.jsonrpc;

public interface JsonRpcService {
    default void _onConnect(JsonRpcSender sender) {
    }

    default void _onClose() {
    }

    default void _onError(Throwable error) {
    }
}
