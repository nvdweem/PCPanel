package dev.niels.wavelink.impl.rpc;

import javax.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import dev.niels.wavelink.impl.model.WaveLinkMainOutput;
import dev.niels.wavelink.impl.model.WaveLinkOutputDevice;
import dev.niels.wavelink.impl.rpc.WaveLinkSetOutputDeviceCommand.WaveLinkSetOutputDeviceParams;

public class WaveLinkSetOutputDeviceCommand extends WaveLinkJsonRpcCommand<WaveLinkSetOutputDeviceParams, WaveLinkOutputDevice> {
    @Override
    public Class<WaveLinkOutputDevice> getResultClass() {
        return WaveLinkOutputDevice.class;
    }

    @JsonInclude(Include.NON_NULL)
    public record WaveLinkSetOutputDeviceParams(
            @Nullable WaveLinkOutputDevice outputDevice,
            @Nullable WaveLinkMainOutput mainOutput
    ) {
    }
}
