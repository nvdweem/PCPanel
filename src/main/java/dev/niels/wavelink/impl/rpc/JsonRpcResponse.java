package dev.niels.wavelink.impl.rpc;

import com.fasterxml.jackson.databind.JsonNode;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public final class JsonRpcResponse implements JsonRpcMessage {
    private String jsonrpc;
    private Long id;
    private JsonNode result;
    private ErrorDetail error;

    @Getter
    @Setter
    public static class ErrorDetail {
        private int code;
        private String message;
        private JsonNode data;
    }
}
