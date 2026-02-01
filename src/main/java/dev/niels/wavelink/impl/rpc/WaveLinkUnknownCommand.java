package dev.niels.wavelink.impl.rpc;

import java.util.Map;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class WaveLinkUnknownCommand extends WaveLinkJsonRpcCommand<Map<String, Object>, Object> {
    private String method;
}
