package dev.niels.wavelink;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import dev.niels.wavelink.impl.model.WaveLinkChannel;
import dev.niels.wavelink.impl.model.WaveLinkControlAction;
import dev.niels.wavelink.impl.model.WaveLinkEffect;
import dev.niels.wavelink.impl.model.WaveLinkInputDevice;
import dev.niels.wavelink.impl.model.WaveLinkMix;
import dev.niels.wavelink.impl.model.WaveLinkOutput;
import dev.niels.wavelink.impl.model.WaveLinkOutputDevice;

public interface IWaveLinkClient {
    @Nonnull
    WaveLinkInputDevice getInputDeviceFromId(String id);

    @Nonnull
    WaveLinkOutputDevice getOutputDeviceFromId(String id);

    @Nonnull
    WaveLinkChannel getChannelFromId(String id);

    @Nonnull
    WaveLinkMix getMixFromId(String id);

    void setInputLevel(WaveLinkInputDevice device, WaveLinkControlAction action, double value);

    void setInputMute(WaveLinkInputDevice device, WaveLinkControlAction action, boolean mute);

    void setInputAudioEffect(WaveLinkInputDevice device, WaveLinkEffect effect);

    default void setChannelLevel(WaveLinkChannel channel, WaveLinkMix mix, double value) {
        setChannel(channel, mix, value, null);
    }

    default void setChannel(WaveLinkChannel channel, WaveLinkMix mix, boolean mute) {
        setChannel(channel, mix, null, mute);
    }

    default void setChannel(String channelId, String mixId, Double value, Boolean mute) {
        setChannel(getChannelFromId(channelId), getMixFromId(mixId), value, mute);
    }

    void setChannel(WaveLinkChannel channel, WaveLinkMix mix, @Nullable Double value, @Nullable Boolean mute);

    void addCurrentToChannel(WaveLinkChannel channel);

    void setChannelAudioEffect(WaveLinkChannel channel, WaveLinkEffect effect);

    default void setMixLevel(WaveLinkMix mix, double value) {
        setMix(mix, value, null);
    }

    default void setMixMute(WaveLinkMix mix, boolean mute) {
        setMix(mix, null, mute);
    }

    void setMix(WaveLinkMix mix, @Nullable Double value, @Nullable Boolean mute);

    void setMixOutput(WaveLinkOutputDevice outputDevice, WaveLinkOutput mix);

    default void setOutputLevel(WaveLinkOutputDevice outputDevice, double value) {
        setOutput(outputDevice, value, null);
    }

    default void setOutputMute(WaveLinkOutputDevice outputDevice, boolean mute) {
        setOutput(outputDevice, null, mute);
    }

    void setOutput(WaveLinkOutputDevice outputDevice, @Nullable Double value, @Nullable Boolean mute);
}
