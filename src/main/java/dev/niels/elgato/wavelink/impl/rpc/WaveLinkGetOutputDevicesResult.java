package dev.niels.elgato.wavelink.impl.rpc;

import java.util.List;

import dev.niels.elgato.wavelink.impl.model.WaveLinkMainOutput;
import dev.niels.elgato.wavelink.impl.model.WaveLinkOutputDevice;

public record WaveLinkGetOutputDevicesResult(
        WaveLinkMainOutput mainOutput,
        List<WaveLinkOutputDevice> outputDevices
) {
}
