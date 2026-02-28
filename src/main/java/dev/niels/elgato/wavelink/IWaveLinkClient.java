package dev.niels.elgato.wavelink;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import dev.niels.elgato.wavelink.impl.model.WaveLinkChannel;
import dev.niels.elgato.wavelink.impl.model.WaveLinkControlAction;
import dev.niels.elgato.wavelink.impl.model.WaveLinkEffect;
import dev.niels.elgato.wavelink.impl.model.WaveLinkInputDevice;
import dev.niels.elgato.wavelink.impl.model.WaveLinkMix;
import dev.niels.elgato.wavelink.impl.model.WaveLinkOutput;
import dev.niels.elgato.wavelink.impl.model.WaveLinkOutputDevice;

public interface IWaveLinkClient {
    boolean isInitialized();

    boolean isConnected();

    void ping();

    CompletableFuture<Void> reconnect();

    CompletableFuture<Void> disconnect();

    void addListener(IWaveLinkClientEventListener listener);

    void removeListener(IWaveLinkClientEventListener listener);

    Map<String, WaveLinkInputDevice> getInputDevices();

    Map<String, WaveLinkOutputDevice> getOutputDevices();

    Map<String, WaveLinkChannel> getChannels();

    Map<String, WaveLinkMix> getMixes();

    @Nonnull
    default WaveLinkInputDevice getInputFromId(String id) {
        return getInputDevices().getOrDefault(id, new WaveLinkInputDevice(id, null, null, null));
    }

    @Nonnull
    default WaveLinkOutputDevice getOutputFromId(String id) {
        return getOutputDevices().getOrDefault(id, new WaveLinkOutputDevice(id, null, null, null));
    }

    @Nonnull
    default WaveLinkChannel getChannelFromId(String id) {
        return getChannels().getOrDefault(id, new WaveLinkChannel(id, null, null, null, null, null, null, null, null));
    }

    @Nonnull
    default WaveLinkMix getMixFromId(String id) {
        return getMixes().getOrDefault(id, new WaveLinkMix(id, null, null, null, null));
    }

    default void setInputLevel(String deviceId, WaveLinkControlAction action, double value) {
        setInputLevel(getInputFromId(deviceId), action, value);
    }

    default void setInputLevel(WaveLinkInputDevice device, WaveLinkControlAction action, double value) {
        setInput(device, action, value, null);
    }

    default void setInputMute(WaveLinkInputDevice device, WaveLinkControlAction action, boolean mute) {
        setInput(device, action, null, mute);
    }

    void setInput(WaveLinkInputDevice device, WaveLinkControlAction action, @Nullable Double value, @Nullable Boolean mute);

    void setInputAudioEffect(WaveLinkInputDevice device, WaveLinkEffect effect);

    default void setChannelLevel(String channelId, double value) {
        setChannel(channelId, null, value, null);
    }

    default void setChannelLevel(WaveLinkChannel channel, double value) {
        setChannel(channel, null, value, null);
    }

    default void setChannelLevel(String channel, String mix, double value) {
        setChannel(channel, mix, value, null);
    }

    default void setChannelLevel(WaveLinkChannel channel, WaveLinkMix mix, double value) {
        setChannel(channel, mix, value, null);
    }

    default void setChannelMute(String channel, String mix, boolean mute) {
        setChannel(channel, mix, null, mute);
    }

    default void setChannelMute(WaveLinkChannel channel, WaveLinkMix mix, boolean mute) {
        setChannel(channel, mix, null, mute);
    }

    default void setChannelMute(String channelId, boolean mute) {
        setChannel(getChannelFromId(channelId), null, null, mute);
    }

    default void setChannelMute(WaveLinkChannel channel, boolean mute) {
        setChannel(channel, null, null, mute);
    }

    default void setChannel(String channelId, Double value, Boolean mute) {
        setChannel(channelId, null, value, mute);
    }

    default void setChannel(String channelId, @Nullable String mixId, @Nullable Double value, @Nullable Boolean mute) {
        setChannel(getChannelFromId(channelId), mixId == null ? null : getMixFromId(mixId), value, mute);
    }

    void setChannel(WaveLinkChannel channel, @Nullable WaveLinkMix mix, @Nullable Double value, @Nullable Boolean mute);

    void setChannel(WaveLinkChannel channel);

    default void addCurrentToChannel(String channelId) {
        addCurrentToChannel(getChannelFromId(channelId));
    }

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

    default void setOutputLevel(String outputDeviceId, double value) {
        setOutput(getOutputFromId(outputDeviceId), value, null);
    }

    default void setOutputLevel(WaveLinkOutputDevice outputDevice, double value) {
        setOutput(outputDevice, value, null);
    }

    default void setOutputMute(String outputDeviceId, boolean mute) {
        setOutput(getOutputFromId(outputDeviceId), null, mute);
    }

    default void setOutputMute(WaveLinkOutputDevice outputDevice, boolean mute) {
        setOutput(outputDevice, null, mute);
    }

    void setOutput(WaveLinkOutputDevice outputDevice, @Nullable Double value, @Nullable Boolean mute);

    default void setMainOutput(String id) {
        setMainOutput(getOutputFromId(id));
    }

    void setMainOutput(WaveLinkOutputDevice outputDevice);

    String getMainOutputDeviceId();
}
