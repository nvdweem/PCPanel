package dev.niels.elgato.wavelink.impl.rpc;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class WaveLinkUnknownCommand extends WaveLinkJsonRpcCommand<Map<String, Object>, Object> {
    private String method;

    @JsonCreator
    public WaveLinkUnknownCommand(@JsonProperty("method") String method) {
        this.method = method;
    }

    @Override
    public String toString() {
        return "WaveLinkUnknownCommand{" +
                "method='" + method + '\'' +
                "params='" + getParams() + '\'' +
                '}';
    }
}
