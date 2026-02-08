package dev.niels.wavelink.impl;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import dev.niels.wavelink.IWaveLinkClient;
import dev.niels.wavelink.IWaveLinkClientEventListener;
import dev.niels.wavelink.impl.model.WaveLinkApp;
import dev.niels.wavelink.impl.model.WaveLinkChannel;
import dev.niels.wavelink.impl.model.WaveLinkControlAction;
import dev.niels.wavelink.impl.model.WaveLinkEffect;
import dev.niels.wavelink.impl.model.WaveLinkInputDevice;
import dev.niels.wavelink.impl.model.WaveLinkMainOutput;
import dev.niels.wavelink.impl.model.WaveLinkMix;
import dev.niels.wavelink.impl.model.WaveLinkOutput;
import dev.niels.wavelink.impl.model.WaveLinkOutputDevice;
import dev.niels.wavelink.impl.model.WithId;
import dev.niels.wavelink.impl.rpc.WaveLinkAddToChannelCommand;
import dev.niels.wavelink.impl.rpc.WaveLinkAddToChannelCommand.WaveLinkAddToChannelParams;
import dev.niels.wavelink.impl.rpc.WaveLinkChannelChangedCommand;
import dev.niels.wavelink.impl.rpc.WaveLinkChannelsChangedCommand;
import dev.niels.wavelink.impl.rpc.WaveLinkFocusedAppChangedCommand;
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
    private CompletableFuture<WebSocket> websocket = CompletableFuture.completedFuture(null);
    private WaveLinkListener waveLinkListener;
    private final HttpClient client;
    private final List<IWaveLinkClientEventListener> listeners = new ArrayList<>();
    @Getter private final Map<String, WaveLinkInputDevice> inputDevices = new ConcurrentHashMap<>();
    @Getter private final Map<String, WaveLinkOutputDevice> outputDevices = new ConcurrentHashMap<>();
    @Getter private final Map<String, WaveLinkChannel> channels = new ConcurrentHashMap<>();
    @Getter private final Map<String, WaveLinkMix> mixes = new ConcurrentHashMap<>();
    @Getter private boolean initialized;
    @Getter private String mainOutputDeviceId;
    @Getter private WaveLinkApp lastFocusApp = WaveLinkApp.EMPTY;

    protected WaveLinkClientImpl(boolean autoConnect) {
        client = HttpClient.newHttpClient();
        if (autoConnect) {
            connect();
        }
    }

    @Override
    public boolean isConnected() {
        var ws = websocket.getNow(null);
        if (ws == null) {
            return false;
        }
        return !ws.isInputClosed() && !ws.isOutputClosed();
    }

    @Override
    public void ping() {
        Optional.ofNullable(websocket.getNow(null))
                .ifPresent(ws ->
                        ws.sendPing(ByteBuffer.wrap("ping".getBytes())).exceptionally(ex -> {
                            if (ex instanceof IOException) {
                                ensureDisconnect();
                            }
                            return null;
                        })
                );
    }

    private void ensureDisconnect() {
        var currentWs = websocket.getNow(null);
        if (currentWs != null) {
            currentWs.abort();
        }
        websocket = CompletableFuture.completedFuture(null);
    }

    private CompletableFuture<WebSocket> connect() {
        ensureDisconnect();

        log.debug("Connecting");
        waveLinkListener = new WaveLinkListener(this);
        websocket = client.newWebSocketBuilder()
                          .header("Origin", "streamdeck://")
                          .buildAsync(URI.create("ws://127.0.0.1:1884"), waveLinkListener)
                          .exceptionally(ex -> {
                              trigger(l -> l.onError(ex));
                              return null;
                          });
        return websocket;
    }

    @Override
    public CompletableFuture<Void> reconnect() {
        return disconnect()
                .thenAccept(x -> connect())
                .exceptionallyCompose(ex -> connect().thenAccept(x -> log.error("Connect after error", ex)));
    }

    @Override
    public CompletableFuture<Void> disconnect() {
        if (isConnected()) {
            var wsa = new WebSocket[1];
            return websocket
                    .thenCompose(
                            ws -> {
                                wsa[0] = ws;
                                return ws == null
                                        ? CompletableFuture.completedFuture(null)
                                        : ws.sendClose(WebSocket.NORMAL_CLOSURE, "Reconnecting").thenAccept(x -> {
                                });
                            }
                    )
                    .exceptionally(ex -> {
                        log.warn("Error disconnecting websocket", ex);
                        if (wsa[0] != null) {
                            wsa[0].abort();
                        }
                        return null;
                    });
        }
        return CompletableFuture.completedFuture(null);
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
        send(new WaveLinkSetChannelCommand().setParams(channel));
    }

    @Override
    public void addCurrentToChannel(WaveLinkChannel channel) {
        if (lastFocusApp.isEmpty()) {
            log.info("WaveLink does not have focus app set, cannot add current to channel");
            return;
        }
        send(new WaveLinkAddToChannelCommand().setParams(
                new WaveLinkAddToChannelParams(lastFocusApp.id(), channel.id())
        ));
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
        if (socket != null && (!socket.isInputClosed() || !socket.isOutputClosed())) {
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

    void onCommand(WaveLinkJsonRpcCommand<?, ?> message) {
        switch (message) {
            case WaveLinkChannelChangedCommand channelChanged -> updateEntry(IWaveLinkClientEventListener::channelChanged, channels, channelChanged.getParams());
            case WaveLinkChannelsChangedCommand channelsChanged -> updateEntries(IWaveLinkClientEventListener::channelsChanged, channels, channelsChanged.getParams().channels());
            case WaveLinkOutputDeviceChangedCommand deviceChanged -> updateEntry(IWaveLinkClientEventListener::outputDeviceChanged, outputDevices, deviceChanged.getParams());
            case WaveLinkMixChangedCommand mixChanged -> updateEntry(IWaveLinkClientEventListener::mixChanged, mixes, mixChanged.getParams());
            case WaveLinkFocusedAppChangedCommand appChanged -> setLastFocusApp(appChanged.getParams());
            default -> log.info("Received unhandled message: {}", message);
        }
    }

    private void setLastFocusApp(WaveLinkApp app) {
        lastFocusApp = app;
        trigger(l -> l.focusedAppChanged(app));
    }

    private <T extends WithId> void updateEntries(Consumer<IWaveLinkClientEventListener> event, Map<String, T> entries, List<T> newEntries) {
        synchronized (entries) {
            entries.clear();
            newEntries.forEach(entry -> entries.put(entry.id(), entry));
        }
        trigger(event);
    }

    private <T extends WithId> void updateEntry(BiConsumer<IWaveLinkClientEventListener, T> event, Map<String, T> entries, T entry) {
        entries.put(entry.id(), entry);
        trigger(e -> event.accept(e, entry));
    }

    void updateInputDevices(List<WaveLinkInputDevice> waveLinkInputDevices) {
        updateEntries(IWaveLinkClientEventListener::inputDevicesChanged, inputDevices, waveLinkInputDevices);
    }

    void updateOutputDevices(List<WaveLinkOutputDevice> waveLinkOutputDevices, WaveLinkMainOutput waveLinkMainOutput) {
        mainOutputDeviceId = waveLinkMainOutput.outputDeviceId();
        updateEntries(IWaveLinkClientEventListener::outputDevicesChanged, outputDevices, waveLinkOutputDevices);
    }

    void updateChannels(List<WaveLinkChannel> channels) {
        updateEntries(IWaveLinkClientEventListener::channelsChanged, this.channels, channels);
    }

    void updateMixes(List<WaveLinkMix> mixes) {
        updateEntries(IWaveLinkClientEventListener::mixesChanged, this.mixes, mixes);
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
