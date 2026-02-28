package dev.niels.elgato.wavelink.impl;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import dev.niels.elgato.jsonrpc.JsonRpcClient;
import dev.niels.elgato.wavelink.IWaveLinkClient;
import dev.niels.elgato.wavelink.IWaveLinkClientEventListener;
import dev.niels.elgato.wavelink.impl.model.WaveLinkChannel;
import dev.niels.elgato.wavelink.impl.model.WaveLinkControlAction;
import dev.niels.elgato.wavelink.impl.model.WaveLinkEffect;
import dev.niels.elgato.wavelink.impl.model.WaveLinkInputDevice;
import dev.niels.elgato.wavelink.impl.model.WaveLinkMainOutput;
import dev.niels.elgato.wavelink.impl.model.WaveLinkMix;
import dev.niels.elgato.wavelink.impl.model.WaveLinkOutput;
import dev.niels.elgato.wavelink.impl.model.WaveLinkOutputDevice;
import dev.niels.elgato.wavelink.impl.rpc.WaveLinkAddToChannelParams;
import dev.niels.elgato.wavelink.impl.rpc.WaveLinkSetOutputDeviceParams;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;

@Log4j2
public abstract class WaveLinkClientImpl implements IWaveLinkClient, AutoCloseable {
    private final WaveLinkService service;
    private final JsonRpcClient jsonRpc;
    private final WaveLinkRpcCommands commands;

    private final List<IWaveLinkClientEventListener> listeners = new CopyOnWriteArrayList<>();
    @Getter private boolean initialized;

    protected WaveLinkClientImpl(boolean autoConnect) {
        service = new WaveLinkService(this);
        jsonRpc = new JsonRpcClient("ws://127.0.0.1:1884", false, service);
        commands = jsonRpc.buildCommands(WaveLinkRpcCommands.class);
        service.setCommands(commands);
        if (autoConnect) {
            jsonRpc.connect();
        }
    }

    @Override
    public boolean isConnected() {
        return jsonRpc.isConnected();
    }

    @Override
    public void ping() {
        jsonRpc.ping();
    }

    @Override
    public CompletableFuture<Void> reconnect() {
        return jsonRpc.reconnect();
    }

    @Override
    public CompletableFuture<Void> disconnect() {
        return jsonRpc.disconnect();
    }

    @Override
    public void addListener(IWaveLinkClientEventListener listener) {
        listeners.add(listener);
    }

    @Override
    public void removeListener(IWaveLinkClientEventListener listener) {
        listeners.remove(listener);
    }

    @Override
    public Map<String, WaveLinkInputDevice> getInputDevices() {
        return service.getInputDevices();
    }

    @Override
    public Map<String, WaveLinkOutputDevice> getOutputDevices() {
        return service.getOutputDevices();
    }

    @Override
    public Map<String, WaveLinkChannel> getChannels() {
        return service.getChannels();
    }

    @Override
    public Map<String, WaveLinkMix> getMixes() {
        return service.getMixes();
    }

    @Override
    public void setInput(WaveLinkInputDevice device, WaveLinkControlAction action, Double value, Boolean mute) {
        log.warn("setInputLevel not implemented yet");
    }

    @Override
    public void setInputAudioEffect(WaveLinkInputDevice device, WaveLinkEffect effect) {
        log.warn("setInputAudioEffect not implemented yet");
    }

    @Override
    public void setChannel(WaveLinkChannel channel, @Nullable WaveLinkMix mix, @Nullable Double value, @Nullable Boolean mute) {
        var newChannel = channel.blank();
        if (mix == null) {
            newChannel = newChannel.withLevel(value).withIsMuted(mute);
        } else {
            newChannel = newChannel.withMixes(List.of(mix.withLevel(value).withIsMuted(mute)));
        }
        setChannel(newChannel);
    }

    @Override
    public void setChannel(WaveLinkChannel channel) {
        commands.setChannel(channel);
    }

    @Override
    public void addCurrentToChannel(WaveLinkChannel channel) {
        if (service.getLastFocusApp().isEmpty()) {
            log.info("WaveLink does not have focus app set, cannot add current to channel");
            return;
        }
        commands.addToChannel(new WaveLinkAddToChannelParams(service.getLastFocusApp().id(), channel.id()));
    }

    @Override
    public void setChannelAudioEffect(WaveLinkChannel channel, WaveLinkEffect effect) {
        commands.setChannel(channel.blank().withEffects(List.of(effect.blank().withIsEnabled(effect.isEnabled()))));
    }

    @Override
    public void setMix(WaveLinkMix mix, @Nullable Double value, @Nullable Boolean mute) {
        commands.setMix(mix.blank().withLevel(value).withIsMuted(mute));
    }

    @Override
    public void setMixOutput(WaveLinkOutputDevice outputDevice, WaveLinkOutput output) {
        commands.setOutputDevice(new WaveLinkSetOutputDeviceParams(
                outputDevice.blank().withOutputs(List.of(output)),
                null
        ));
    }

    @Override
    public void setOutput(WaveLinkOutputDevice outputDevice, @Nullable Double value, @Nullable Boolean mute) {
        if (outputDevice.outputs() == null)
            return;

        commands.setOutputDevice(new WaveLinkSetOutputDeviceParams(
                outputDevice.blankWithOutputs()
                            .withOutputs(o -> o.blank().withLevel(value).withIsMuted(mute)),
                null
        ));
    }

    @Override
    public void setMainOutput(WaveLinkOutputDevice outputDevice) {
        commands.setOutputDevice(new WaveLinkSetOutputDeviceParams(
                null,
                new WaveLinkMainOutput(outputDevice.id())
        ));
    }

    @Override
    public String getMainOutputDeviceId() {
        return "";
    }

    @Nonnull
    public WaveLinkInputDevice getInputDeviceFromId(String id) {
        return service.getInputDevices().getOrDefault(id, new WaveLinkInputDevice(id, null, null, null));
    }

    @Nonnull
    public WaveLinkOutputDevice getOutputDeviceFromId(String id) {
        return service.getOutputDevices().getOrDefault(id, new WaveLinkOutputDevice(id, null, null, null));
    }

    @Override
    @Nonnull
    public WaveLinkChannel getChannelFromId(String id) {
        return service.getChannels().getOrDefault(id, new WaveLinkChannel(id, null, null, null, null, null, null, null, null));
    }

    @Override
    @Nonnull
    public WaveLinkMix getMixFromId(String id) {
        return service.getMixes().getOrDefault(id, new WaveLinkMix(id, null, null, null, null));
    }

    @Override
    public void close() {
        jsonRpc.close();
    }

    void trigger(Consumer<IWaveLinkClientEventListener> event) {
        listeners.forEach(event);
    }

    public void setInitialized() {
        log.info("Connected to Wave Link and initialized");
        initialized = true;
        trigger(IWaveLinkClientEventListener::initialized);
    }
}
