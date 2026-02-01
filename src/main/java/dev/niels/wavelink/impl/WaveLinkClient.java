package dev.niels.wavelink.impl;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import dev.niels.wavelink.impl.model.Mergable;
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
import dev.niels.wavelink.impl.rpc.WaveLinkSetChannelCommand;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class WaveLinkClient implements AutoCloseable {
    private final CompletableFuture<WebSocket> websocket;
    private final WaveLinkListener waveLinkListener;
    @Getter private final List<WaveLinkInputDevice> inputDevices = new ArrayList<>();
    @Getter private final List<WaveLinkOutputDevice> outputDevices = new ArrayList<>();
    @Getter private final List<WaveLinkChannel> channels = new ArrayList<>();
    @Getter private final List<WaveLinkMix> mixes = new ArrayList<>();
    private boolean initialized;

    public WaveLinkClient() {
        var client = HttpClient.newHttpClient();

        log.info("Connecting");
        waveLinkListener = new WaveLinkListener(this);
        websocket = client.newWebSocketBuilder()
                          .header("Origin", "streamdeck://")
                          // .buildAsync(URI.create("ws://127.0.0.1:1885"), new WaveLinkListener(this));
                          .buildAsync(URI.create("ws://127.0.0.1:1884"), waveLinkListener);
    }

    @Override
    public void close() {
        var socket = websocket.join();
        if (!socket.isInputClosed() || !socket.isOutputClosed()) {
            socket.sendClose(WebSocket.NORMAL_CLOSURE, "Done");
        }
    }

    public CompletableFuture<WaveLinkChannel> setChannel(WaveLinkChannel channel) {
        return send(new WaveLinkSetChannelCommand().setParams(channel));
    }

    <R> CompletableFuture<R> send(WaveLinkJsonRpcCommand<?, R> message) {
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

    private <T extends WithId> void updateEntries(List<T> entries, List<T> newEntries) {
        entries.clear();
        entries.addAll(newEntries);
    }

    private <T extends WithId> void updateEntry(List<T> entries, T entry) {
        synchronized (entries) {
            for (var itt = entries.listIterator(); itt.hasNext(); ) {
                var item = itt.next();
                if (entry.id().equals(item.id())) {
                    if (item instanceof Mergable m) {
                        itt.set((T) m.merge(entry));
                    } else {
                        itt.set(entry);
                    }
                    return;
                }
            }
            entries.add(entry);
        }
    }

    void updateInputDevices(List<WaveLinkInputDevice> waveLinkInputDevices) {
        updateEntries(inputDevices, waveLinkInputDevices);
    }

    void updateOutputDevices(List<WaveLinkOutputDevice> waveLinkOutputDevices, WaveLinkMainOutput waveLinkMainOutput) {
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
