package dev.niels.elgato.shared;

import dev.niels.elgato.jsonrpc.IJsonRpcClientListener;

public interface IRpcListener extends IJsonRpcClientListener {
    default void initialized() {
    }
}
