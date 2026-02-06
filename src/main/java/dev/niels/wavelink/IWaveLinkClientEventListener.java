package dev.niels.wavelink;

import dev.niels.wavelink.impl.model.WaveLinkApp;
import dev.niels.wavelink.impl.model.WaveLinkChannel;
import dev.niels.wavelink.impl.model.WaveLinkMix;
import dev.niels.wavelink.impl.model.WaveLinkOutputDevice;

public interface IWaveLinkClientEventListener {
    default void inputDevicesChanged() {
    }

    default void channelChanged(WaveLinkChannel channel) {
    }

    default void channelsChanged() {
    }

    default void mixesChanged() {
    }

    default void mixChanged(WaveLinkMix mix) {
    }

    default void outputDevicesChanged() {
    }

    default void outputDeviceChanged(WaveLinkOutputDevice outputDevice) {
    }

    default void focusedAppChanged(WaveLinkApp app) {
    }
}
