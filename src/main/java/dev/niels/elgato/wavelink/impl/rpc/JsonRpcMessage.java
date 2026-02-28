package dev.niels.elgato.wavelink.impl.rpc;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.As;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;

@JsonTypeInfo(use = Id.NAME, include = As.EXISTING_PROPERTY, property = "method", visible = true, defaultImpl = JsonRpcResponse.class)
@JsonSubTypes(@Type(WaveLinkJsonRpcCommand.class))
public sealed interface JsonRpcMessage permits JsonRpcResponse, WaveLinkJsonRpcCommand {
}
