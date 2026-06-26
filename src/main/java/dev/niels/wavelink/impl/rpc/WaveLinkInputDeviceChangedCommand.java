package dev.niels.wavelink.impl.rpc;

import dev.niels.wavelink.impl.model.WaveLinkInputDevice;

/**
 * Server push when an input device changes. The payload is partial per change: a gain change carries the
 * input's gain but not its mute, and vice versa — so it must be merged with the cached device.
 */
public class WaveLinkInputDeviceChangedCommand extends WaveLinkJsonRpcCommand<WaveLinkInputDevice, Void> {
}
