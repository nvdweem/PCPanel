package dev.niels.elgato.wavelink.impl;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import dev.niels.elgato.jsonrpc.JsonRpcSender;
import dev.niels.elgato.wavelink.IWaveLinkClientEventListener;
import dev.niels.elgato.wavelink.impl.model.WaveLinkApp;
import dev.niels.elgato.wavelink.impl.model.WaveLinkChannel;
import dev.niels.elgato.wavelink.impl.model.WaveLinkInputDevice;
import dev.niels.elgato.wavelink.impl.model.WaveLinkMainOutput;
import dev.niels.elgato.wavelink.impl.model.WaveLinkMix;
import dev.niels.elgato.wavelink.impl.model.WaveLinkOutputDevice;
import dev.niels.elgato.wavelink.impl.model.WithId;
import dev.niels.elgato.wavelink.impl.rpc.WaveLinkChannelsChangedCommandResult;
import dev.niels.elgato.wavelink.impl.rpc.WaveLinkGetApplicationInfoResult;
import dev.niels.elgato.wavelink.impl.rpc.WaveLinkSetSubscriptionParams;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;

@Log4j2
@RequiredArgsConstructor
class WaveLinkService implements IWaveLinkService {
    private final WaveLinkClientImpl client;
    @Setter(AccessLevel.PACKAGE) private WaveLinkRpcCommands commands;
    @Getter private final Map<String, WaveLinkInputDevice> inputDevices = new ConcurrentHashMap<>();
    @Getter private final Map<String, WaveLinkOutputDevice> outputDevices = new ConcurrentHashMap<>();
    @Getter private final Map<String, WaveLinkChannel> channels = new ConcurrentHashMap<>();
    @Getter private final Map<String, WaveLinkMix> mixes = new ConcurrentHashMap<>();
    @Getter private WaveLinkApp lastFocusApp = WaveLinkApp.EMPTY;
    @Getter private String mainOutputDeviceId;

    @Override
    public void _onConnect(JsonRpcSender sender) {
        commands.getApplicationInfo()
                .thenAccept(res -> {
                    ensureCorrectVersion(res);
                    log.debug("Connected to Wave Link, getting info");
                    getInfo();
                });
        client.trigger(IWaveLinkClientEventListener::connected);
    }

    @Override
    public void _onClose() {
        client.trigger(IWaveLinkClientEventListener::connectionClosed);
    }

    @Override
    public void _onError(Throwable error) {
        client.trigger(l -> l.onError(error));
    }

    private void getInfo() {
        CompletableFuture.allOf(
                commands.getInputDevices().thenAccept(res -> updateInputDevices(res.inputDevices())).toCompletableFuture(),
                commands.getOutputDevices().thenAccept(res -> updateOutputDevices(res.outputDevices(), res.mainOutput())).toCompletableFuture(),
                commands.getChannels().thenAccept(res -> updateChannels(res.channels())).toCompletableFuture(),
                commands.getMixes().thenAccept(res -> updateMixes(res.mixes())).toCompletableFuture(),
                commands.setSubscription(new WaveLinkSetSubscriptionParams(true, null)).thenAccept(res -> {
                    log.debug("Successfully subscribed to websocket events: {}", res);
                }).toCompletableFuture()
        ).thenRun(client::setInitialized);
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

    private void ensureCorrectVersion(WaveLinkGetApplicationInfoResult res) {
        log.info("Connected websocket, wavelink info: {}", res);
        var correctAppId = "ewl".equalsIgnoreCase(res.appID());
        var correctAppName = "Elgato Wave Link".equalsIgnoreCase(res.name());
        if (!correctAppId || !correctAppName) {
            throw new IllegalStateException("Expected appId ewl and appName Elgato Wave Link, got " + res);
        }
    }

    @Override
    public void channelChanged(WaveLinkChannel channel) {
        updateEntry(IWaveLinkClientEventListener::channelChanged, channels, channel);
    }

    @Override
    public void channelsChanged(WaveLinkChannelsChangedCommandResult channelsChangedResult) {
        updateEntries(IWaveLinkClientEventListener::channelsChanged, channels, channelsChangedResult.channels());
    }

    @Override
    public void outputDeviceChanged(WaveLinkOutputDevice outputDevice) {
        updateEntry(IWaveLinkClientEventListener::outputDeviceChanged, outputDevices, outputDevice);
    }

    @Override
    public void mixChanged(WaveLinkMix mix) {
        updateEntry(IWaveLinkClientEventListener::mixChanged, mixes, mix);
    }

    @Override
    public void focusedAppChanged(WaveLinkApp app) {
        lastFocusApp = app;
        client.trigger(l -> l.focusedAppChanged(app));
    }

    private <T extends WithId> void updateEntry(BiConsumer<IWaveLinkClientEventListener, T> event, Map<String, T> entries, T entry) {
        entries.put(entry.id(), entry);
        client.trigger(e -> event.accept(e, entry));
    }

    private <T extends WithId> void updateEntries(Consumer<IWaveLinkClientEventListener> event, Map<String, T> entries, List<T> newEntries) {
        synchronized (entries) {
            entries.clear();
            newEntries.forEach(entry -> entries.put(entry.id(), entry));
        }
        client.trigger(event);
    }
}
