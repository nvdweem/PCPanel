package dev.niels.elgato.wavelink.impl.rpc;

import java.util.List;

import dev.niels.elgato.wavelink.impl.model.WaveLinkInputDevice;
import dev.niels.elgato.wavelink.impl.rpc.WaveLinkGetInputDevices.WaveLinkGetInputDevicesResult;

public class WaveLinkGetInputDevices extends WaveLinkJsonRpcCommand<Void, WaveLinkGetInputDevicesResult> {
    @Override
    public Class<WaveLinkGetInputDevicesResult> getResultClass() {
        return WaveLinkGetInputDevicesResult.class;
    }

    public record WaveLinkGetInputDevicesResult(
            List<WaveLinkInputDevice> inputDevices
    ) {
    }
}
