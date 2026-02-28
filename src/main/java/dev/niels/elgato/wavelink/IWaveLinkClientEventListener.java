package dev.niels.elgato.wavelink;

import dev.niels.elgato.wavelink.impl.model.WaveLinkApp;
import dev.niels.elgato.wavelink.impl.model.WaveLinkChannel;
import dev.niels.elgato.wavelink.impl.model.WaveLinkMix;
import dev.niels.elgato.wavelink.impl.model.WaveLinkOutputDevice;

public interface IWaveLinkClientEventListener {
    default void connected() {
    }

    default void initialized() {
    }

    default void connectionClosed() {
    }

    default void onError(Throwable t) {
    }

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
