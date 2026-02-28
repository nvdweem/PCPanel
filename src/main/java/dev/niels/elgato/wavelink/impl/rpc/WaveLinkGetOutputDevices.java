package dev.niels.elgato.wavelink.impl.rpc;

import java.util.List;

import dev.niels.elgato.wavelink.impl.model.WaveLinkMainOutput;
import dev.niels.elgato.wavelink.impl.model.WaveLinkOutputDevice;
import dev.niels.elgato.wavelink.impl.rpc.WaveLinkGetOutputDevices.WaveLinkGetOutputDevicesResult;

public class WaveLinkGetOutputDevices extends WaveLinkJsonRpcCommand<Void, WaveLinkGetOutputDevicesResult> {
    @Override
    public Class<WaveLinkGetOutputDevicesResult> getResultClass() {
        return WaveLinkGetOutputDevicesResult.class;
    }

    public record WaveLinkGetOutputDevicesResult(
            WaveLinkMainOutput mainOutput,
            List<WaveLinkOutputDevice> outputDevices
    ) {
    }
}
