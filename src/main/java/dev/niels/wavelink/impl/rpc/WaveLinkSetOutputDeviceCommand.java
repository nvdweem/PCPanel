package dev.niels.wavelink.impl.rpc;

import dev.niels.wavelink.impl.model.WaveLinkOutputDevice;
import dev.niels.wavelink.impl.rpc.WaveLinkSetOutputDeviceCommand.WaveLinkSetOutputDeviceParams;

public class WaveLinkSetOutputDeviceCommand extends WaveLinkJsonRpcCommand<WaveLinkSetOutputDeviceParams, WaveLinkOutputDevice> {
    @Override
    public Class<WaveLinkOutputDevice> getResultClass() {
        return WaveLinkOutputDevice.class;
    }

    public record WaveLinkSetOutputDeviceParams(WaveLinkOutputDevice outputDevice) {
    }
}
