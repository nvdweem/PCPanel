package dev.niels.wavelink.impl;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;

import dev.niels.wavelink.impl.model.WaveLinkChannel;
import dev.niels.wavelink.impl.model.WaveLinkInputDevice;
import dev.niels.wavelink.impl.model.WaveLinkMix;
import dev.niels.wavelink.impl.model.WaveLinkOutputDevice;
import dev.niels.wavelink.impl.model.WithId;
import dev.niels.wavelink.impl.rpc.WaveLinkChannelChangedCommand;
import dev.niels.wavelink.impl.rpc.WaveLinkChannelsChangedCommand;
import dev.niels.wavelink.impl.rpc.WaveLinkGetOutputDevices.WaveLinkMainOutput;
import dev.niels.wavelink.impl.rpc.WaveLinkJsonRpcCommand;
import dev.niels.wavelink.impl.rpc.WaveLinkMixChangedCommand;
import dev.niels.wavelink.impl.rpc.WaveLinkOutputDeviceChangedCommand;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;

@Log4j2
public abstract class WaveLinkClientImpl implements AutoCloseable {
    private final CompletableFuture<WebSocket> websocket;
    private final WaveLinkListener waveLinkListener;
    private final HttpClient client;
    @Getter private final Map<String, WaveLinkInputDevice> inputDevices = new ConcurrentHashMap<>();
    @Getter private final Map<String, WaveLinkOutputDevice> outputDevices = new ConcurrentHashMap<>();
    @Getter private final Map<String, WaveLinkChannel> channels = new ConcurrentHashMap<>();
    @Getter private final Map<String, WaveLinkMix> mixes = new ConcurrentHashMap<>();
    private boolean initialized;
    private String mainOutputDeviceId;

    protected WaveLinkClientImpl() {
        client = HttpClient.newHttpClient();

        log.info("Connecting");
        waveLinkListener = new WaveLinkListener(this);
        websocket = client.newWebSocketBuilder()
                          .header("Origin", "streamdeck://")
                          .buildAsync(URI.create("ws://127.0.0.1:1884"), waveLinkListener);
    }

    @Nonnull
    public WaveLinkInputDevice getInputDeviceFromId(String id) {
        return inputDevices.getOrDefault(id, new WaveLinkInputDevice(id, null, null, null));
    }

    @Nonnull
    public WaveLinkOutputDevice getOutputDeviceFromId(String id) {
        return outputDevices.getOrDefault(id, new WaveLinkOutputDevice(id, null, null, null));
    }

    @Nonnull
    public WaveLinkChannel getChannelFromId(String id) {
        return channels.getOrDefault(id, new WaveLinkChannel(id, null, null, null, null, null, null, null, null));
    }

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
