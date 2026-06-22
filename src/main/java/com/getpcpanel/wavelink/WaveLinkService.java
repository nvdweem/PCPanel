package com.getpcpanel.wavelink;

import java.net.ConnectException;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.CompletionException;

import com.getpcpanel.profile.SaveService;
import com.getpcpanel.profile.SaveService.SaveEvent;
import com.getpcpanel.util.ReconnectBackoff;
import com.getpcpanel.volume.IFocusRedirector;

import dev.niels.wavelink.IWaveLinkClientEventListener;
import dev.niels.wavelink.WaveLinkClient;
import dev.niels.wavelink.impl.model.WaveLinkChannel;
import dev.niels.wavelink.impl.model.WaveLinkMix;
import dev.niels.wavelink.impl.model.WaveLinkOutputDevice;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import lombok.extern.log4j.Log4j2;
import one.util.streamex.StreamEx;

@Log4j2
@ApplicationScoped
public class WaveLinkService extends WaveLinkClient implements IWaveLinkClientEventListener, IFocusRedirector {
    private final SaveService saveService;
    /** Spaces out reconnect attempts when Wave Link is down (base = the scheduled interval, capped at 5 min). */
    private final ReconnectBackoff backoff = new ReconnectBackoff(10_000, 300_000);
    private boolean wasEnabled;
    @Inject
    Event<WaveLinkChangedEvent> changedEvent;
    @Inject
    WaveLinkAppCache appCache;

    WaveLinkService() {
        super(false);
        saveService = null;
    }

    /**
     * Routes the focused-app volume to Wave Link when the focused app is one Wave Link controls,
     * returning {@code true} to stop the coordinator from also changing the app's OS volume.
     *
     * <p>While connected the live channels are authoritative: resolve the focused app to a channel
     * (by Wave Link's own focus, then by matching the OS process against channel membership) and set
     * its level. Any app we successfully route is remembered in {@link WaveLinkAppCache}. When not yet
     * connected — notably the startup race (#2), where the initial focus-volume trigger fires before
     * Wave Link is up — we defer to that cache so a known Wave-Link app's OS volume is never touched.
     */
    @Override
    public boolean handleFocusVolumeRequest(String targetProcess, float volume) {
        if (isConnected()) {
            var channelId = findChannelIdForFocusApp().or(() -> findChannelIdForProcess(targetProcess));
            if (channelId.isPresent()) {
                setChannelLevel(channelId.get(), volume);
                if (appCache != null) {
                    appCache.learn(targetProcess);
                }
                return true;
            }
            return false; // connected but the focused app is in no channel → let the OS handle its volume
        }
        // Not connected yet: defer to Wave Link for apps we have previously seen it control.
        return appCache != null && appCache.isControlled(targetProcess);
    }

    /** Finds the channel whose membership includes the given OS process (matched by executable name). */
    private Optional<String> findChannelIdForProcess(String targetProcess) {
        var key = WaveLinkAppCache.normalize(targetProcess);
        if (key.isBlank()) {
            return Optional.empty();
        }
        return StreamEx.ofValues(getChannels())
                       .filter(channel -> StreamEx.of(channel.apps())
                                                  .anyMatch(app -> key.equals(WaveLinkAppCache.normalize(app.id()))
                                                          || key.equals(WaveLinkAppCache.normalize(app.name()))))
                       .map(WaveLinkChannel::id)
                       .findFirst();
    }

    @Inject
    public WaveLinkService(SaveService saveService) {
        super(false);
        this.saveService = saveService;
        wasEnabled = isEnabled();
        addListener(this);
    }

    public boolean isEnabled() {
        return saveService.get().getWaveLink().enabled();
    }

    public void settingsChanged(@Observes SaveEvent event) {
        var is = isEnabled();
        if (wasEnabled && !is) {
            disconnect();
        }
        if (!wasEnabled && is) {
            reconnect();
        }
        wasEnabled = isEnabled();
    }

    @Scheduled(every = "10s")
    public void checkConnection() {
        if (!isEnabled()) {
            backoff.onSuccess(); // nothing to connect → keep the gate clear so enabling reconnects at once
            return;
        }
        if (isConnected()) {
            backoff.onSuccess();
            log.debug("WaveLink connected, sending ping.");
            ping();
            // Keep the controlled-app cache current even when no channelChanged event fires after the
            // initial channel load, so the set is persisted for the next startup's focus-volume race.
            syncAppCache();
        } else if (backoff.ready(System.currentTimeMillis())) {
            log.info("WaveLink not connected, connecting.");
            reconnect();
            // reconnect() is async; record an attempt and let the next tick clear the backoff once connected.
            backoff.onFailure(System.currentTimeMillis());
        }
    }

    @Override
    public void connectionClosed() {
        log.info("WaveLink connection closed.");
        fireChanged();
    }

    // Wave Link state (incl. mute) changed: notify observers (e.g. the mute-colour layer) to re-read state.
    @Override
    public void channelChanged(WaveLinkChannel channel) {
        log.debug("WaveLink channelChanged id={} name={} muted={}", channel.id(), channel.name(), channel.isMuted());
        syncAppCache();
        fireChanged();
    }

    @Override
    public void channelsChanged() {
        syncAppCache();
        fireChanged();
    }

    private void syncAppCache() {
        if (appCache != null) {
            appCache.syncFromChannels(getChannels().values());
        }
    }

    @Override
    public void mixChanged(WaveLinkMix mix) {
        fireChanged();
    }

    @Override
    public void mixesChanged() {
        fireChanged();
    }

    @Override
    public void outputDeviceChanged(WaveLinkOutputDevice outputDevice) {
        fireChanged();
    }

    @Override
    public void outputDevicesChanged() {
        fireChanged();
    }

    private void fireChanged() {
        if (changedEvent != null) {
            changedEvent.fire(new WaveLinkChangedEvent());
        }
    }

    /**
     * Returns the WaveLink channel ID that the current WaveLink-focused application is assigned to,
     * if any. This is determined by searching all known channels for the app that WaveLink currently
     * reports as focused ({@code lastFocusApp}).
     * <p>
     * Returns {@link Optional#empty()} when WaveLink is not connected, has no focused app, or the
     * focused app is not assigned to any channel.
     */
    public Optional<String> findChannelIdForFocusApp() {
        var focusApp = getLastFocusApp();
        if (focusApp.isEmpty()) {
            return Optional.empty();
        }
        return StreamEx.ofValues(getChannels())
                       .mapToEntry(WaveLinkChannel::apps).flatMapValues(Collection::stream)
                       .filterValues(app -> focusApp.id().equals(app.id()))
                       .keys()
                       .map(WaveLinkChannel::id).findFirst();
    }

    @Override
    public void onError(Throwable t) {
        if (t instanceof CompletionException ce) {
            t = ce.getCause();
        }

        if (t instanceof ConnectException) {
            log.warn("Unable to connect to WaveLink");
        }
    }
}
