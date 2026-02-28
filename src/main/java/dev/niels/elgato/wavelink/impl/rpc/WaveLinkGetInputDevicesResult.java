package dev.niels.elgato.wavelink.impl.rpc;

import java.util.List;

import dev.niels.elgato.wavelink.impl.model.WaveLinkInputDevice;

public record WaveLinkGetInputDevicesResult(
        List<WaveLinkInputDevice> inputDevices
) {
}
