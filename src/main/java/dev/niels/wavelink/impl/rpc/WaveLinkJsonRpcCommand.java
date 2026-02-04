package dev.niels.wavelink.impl.rpc;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.As;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;

import lombok.Getter;
import lombok.Setter;

@JsonTypeInfo(use = Id.NAME, include = As.PROPERTY, property = "method", defaultImpl = WaveLinkUnknownCommand.class)
@JsonSubTypes({
        // Called by server to set info
        @Type(value = WaveLinkChannelChangedCommand.class, name = "channelChanged"),
        @Type(value = WaveLinkChannelsChangedCommand.class, name = "channelsChanged"),
        @Type(value = WaveLinkOutputDeviceChangedCommand.class, name = "outputDeviceChanged"),
        @Type(value = WaveLinkMixChangedCommand.class, name = "mixChanged"),

        // Called by client to get info
        @Type(value = WaveLinkGetApplicationInfo.class, name = "getApplicationInfo"),
        @Type(value = WaveLinkGetInputDevices.class, name = "getInputDevices"),
        @Type(value = WaveLinkGetOutputDevices.class, name = "getOutputDevices"),
        @Type(value = WaveLinkGetChannels.class, name = "getChannels"),
        @Type(value = WaveLinkGetMixes.class, name = "getMixes"),

        // Commands
        @Type(value = WaveLinkSetChannelCommand.class, name = "setChannel"),
        @Type(value = WaveLinkSetMixCommand.class, name = "setMix"),
        @Type(value = WaveLinkSetOutputDeviceCommand.class, name = "setOutputDevice"),
})
@Getter
@Setter
public class WaveLinkJsonRpcCommand<T, R> {
    private String jsonrpc = "2.0";
    @JsonInclude(Include.NON_NULL)
    private T params;
    private long id;

    @JsonIgnore
    public Class<R> getResultClass() {
        return (Class<R>) Map.class;
    }
}
