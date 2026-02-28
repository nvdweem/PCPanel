package dev.niels.elgato.jsonrpc;

import javax.annotation.Nullable;

import lombok.With;

record JsonRpcCommand<T>(@With long id, String method, T params, String jsonrpc) {
    public JsonRpcCommand(String method, @Nullable T params) {
        this(0, method, params, "2.0");
    }
}
