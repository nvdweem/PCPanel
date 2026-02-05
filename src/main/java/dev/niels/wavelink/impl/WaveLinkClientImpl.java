package dev.niels.wavelink.impl;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import dev.niels.wavelink.IWaveLinkClient;
import dev.niels.wavelink.impl.model.WaveLinkChannel;
import dev.niels.wavelink.impl.model.WaveLinkControlAction;
import dev.niels.wavelink.impl.model.WaveLinkEffect;
import dev.niels.wavelink.impl.model.WaveLinkInputDevice;
import dev.niels.wavelink.impl.model.WaveLinkMainOutput;
import dev.niels.wavelink.impl.model.WaveLinkMix;
import dev.niels.wavelink.impl.model.WaveLinkOutput;
import dev.niels.wavelink.impl.model.WaveLinkOutputDevice;
import dev.niels.wavelink.impl.model.WithId;
import dev.niels.wavelink.impl.rpc.WaveLinkChannelChangedCommand;
import dev.niels.wavelink.impl.rpc.WaveLinkChannelsChangedCommand;
import dev.niels.wavelink.impl.rpc.WaveLinkJsonRpcCommand;
import dev.niels.wavelink.impl.rpc.WaveLinkMixChangedCommand;
import dev.niels.wavelink.impl.rpc.WaveLinkOutputDeviceChangedCommand;
import dev.niels.wavelink.impl.rpc.WaveLinkSetChannelCommand;
import dev.niels.wavelink.impl.rpc.WaveLinkSetMixCommand;
import dev.niels.wavelink.impl.rpc.WaveLinkSetOutputDeviceCommand;
import dev.niels.wavelink.impl.rpc.WaveLinkSetOutputDeviceCommand.WaveLinkSetOutputDeviceParams;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;

@Log4j2
public abstract class WaveLinkClientImpl implements IWaveLinkClient, AutoCloseable {
    private final CompletableFuture<WebSocket> websocket;
    private final WaveLinkListener waveLinkListener;
    private final HttpClient client;
    @Getter private final Map<String, WaveLinkInputDevice> inputDevices = new ConcurrentHashMap<>();
    @Getter private final Map<String, WaveLinkOutputDevice> outputDevices = new ConcurrentHashMap<>();
    @Getter private final Map<String, WaveLinkChannel> channels = new ConcurrentHashMap<>();
    @Getter private final Map<String, WaveLinkMix> mixes = new ConcurrentHashMap<>();
    @Getter private boolean initialized;
    @Getter private String mainOutputDeviceId;

    protected WaveLinkClientImpl() {
        client = HttpClient.newHttpClient();

        log.info("Connecting");
        waveLinkListener = new WaveLinkListener(this);
        websocket = client.newWebSocketBuilder()
                          .header("Origin", "streamdeck://")
                          .buildAsync(URI.create("ws://127.0.0.1:1884"), waveLinkListener);
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
        send(new WaveLinkSetChannelCommand().setParams(newChannel));
    }

    @Override
    public void addCurrentToChannel(WaveLinkChannel channel) {
        log.warn("addCurrentToChannel not implemented yet");
    }

    @Override
    public void setChannelAudioEffect(WaveLinkChannel channel, WaveLinkEffect effect) {
        send(new WaveLinkSetChannelCommand().setParams(
                channel.blank()
                       .withEffects(List.of(effect.blank().withIsEnabled(effect.isEnabled())))
        ));
    }

    @Override
    public void setMix(WaveLinkMix mix, @Nullable Double value, @Nullable Boolean mute) {
        send(new WaveLinkSetMixCommand().setParams(mix.blank().withLevel(value).withIsMuted(mute)));
    }

    @Override
    public void setMixOutput(WaveLinkOutputDevice outputDevice, WaveLinkOutput output) {
        send(new WaveLinkSetOutputDeviceCommand().setParams(new WaveLinkSetOutputDeviceParams(
                outputDevice.blank().withOutputs(List.of(output)),
                null
        )));
    }

    @Override
    public void setOutput(WaveLinkOutputDevice outputDevice, @Nullable Double value, @Nullable Boolean mute) {
        if (outputDevice.outputs() == null)
            return;

        send(new WaveLinkSetOutputDeviceCommand().setParams(new WaveLinkSetOutputDeviceParams(
                outputDevice.blankWithOutputs()
                            .withOutputs(o -> o.blank().withLevel(value).withIsMuted(mute)),
                null
        )));
    }

    @Override
    public void setMainOutput(WaveLinkOutputDevice outputDevice) {
        send(new WaveLinkSetOutputDeviceCommand().setParams(new WaveLinkSetOutputDeviceParams(
                null,
                new WaveLinkMainOutput(outputDevice.id())
        )));
    }

    @Nonnull
    public WaveLinkInputDevice getInputDeviceFromId(String id) {
        return inputDevices.getOrDefault(id, new WaveLinkInputDevice(id, null, null, null));
    }

    @Nonnull
    public WaveLinkOutputDevice getOutputDeviceFromId(String id) {
        return outputDevices.getOrDefault(id, new WaveLinkOutputDevice(id, null, null, null));
    }

    @Override
    @Nonnull
    public WaveLinkChannel getChannelFromId(String id) {
        return channels.getOrDefault(id, new WaveLinkChannel(id, null, null, null, null, null, null, null, null));
    }

    @Override
    @Nonnull
    public WaveLinkMix getMixFromId(String id) {
        return mixes.getOrDefault(id, new WaveLinkMix(id, null, null, null, null));
    }

    @Override
    public void close() {
        var socket = websocket.join();
        if (!socket.isInputClosed() || !socket.isOutputClosed()) {
            socket.sendClose(WebSocket.NORMAL_CLOSURE, "Done");
        }
        try {
            client.close();
        } catch (Exception e) {
            log.error("Error closing websocket", e);
        }
    }

    protected <R> CompletableFuture<R> send(WaveLinkJsonRpcCommand<?, R> message) {
        return waveLinkListener.sendExpectingResult(message);
    }

    void onMessage(WaveLinkJsonRpcCommand<?, ?> message) {
        switch (message) {
            case WaveLinkChannelChangedCommand channelChanged -> updateEntry(channels, channelChanged.getParams());
            case WaveLinkChannelsChangedCommand channelsChanged -> updateEntries(channels, channelsChanged.getParams().channels());

            case WaveLinkOutputDeviceChangedCommand deviceChanged -> updateEntry(outputDevices, deviceChanged.getParams());
            case WaveLinkMixChangedCommand mixChanged -> updateEntry(mixes, mixChanged.getParams());
            default -> {
                log.info("Received unhandled message: {}", message);
            }
        }
    }

    private <T extends WithId> void updateEntries(Map<String, T> entries, List<T> newEntries) {
        synchronized (entries) {
            entries.clear();
            newEntries.forEach(entry -> entries.put(entry.id(), entry));
        }
    }

    private <T extends WithId> void updateEntry(Map<String, T> entries, T entry) {
        entries.put(entry.id(), entry);
    }

    void updateInputDevices(List<WaveLinkInputDevice> waveLinkInputDevices) {
        updateEntries(inputDevices, waveLinkInputDevices);
    }

    void updateOutputDevices(List<WaveLinkOutputDevice> waveLinkOutputDevices, WaveLinkMainOutput waveLinkMainOutput) {
        mainOutputDeviceId = waveLinkMainOutput.outputDeviceId();
        updateEntries(outputDevices, waveLinkOutputDevices);
    }

    void updateChannels(List<WaveLinkChannel> channels) {
        updateEntries(this.channels, channels);
    }

    void updateMixes(List<WaveLinkMix> mixes) {
        updateEntries(this.mixes, mixes);
    }

    public void setInitialized() {
        log.info("Connected to Wave Link and initialized");
        initialized = true;
    }
}
