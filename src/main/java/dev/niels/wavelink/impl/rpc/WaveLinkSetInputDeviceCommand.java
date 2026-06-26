package dev.niels.wavelink.impl.rpc;

import dev.niels.wavelink.impl.model.WaveLinkInputDevice;

/**
 * Sets an input device's gain/mute. Unlike {@code setOutputDevice} (which wraps its payload), Wave Link
 * expects the {@link WaveLinkInputDevice} as the bare params object; it echoes the device back.
 */
public class WaveLinkSetInputDeviceCommand extends WaveLinkJsonRpcCommand<WaveLinkInputDevice, WaveLinkInputDevice> {
    @Override
    public Class<WaveLinkInputDevice> getResultClass() {
        return WaveLinkInputDevice.class;
    }
}
