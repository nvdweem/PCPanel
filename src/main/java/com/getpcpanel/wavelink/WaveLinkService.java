package com.getpcpanel.wavelink;

import java.net.ConnectException;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.CompletionException;

import com.getpcpanel.profile.SaveService;
import com.getpcpanel.volume.IFocusRedirector;

import dev.niels.wavelink.IWaveLinkClientEventListener;
import dev.niels.wavelink.WaveLinkClient;
import dev.niels.wavelink.impl.model.WaveLinkChannel;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.log4j.Log4j2;
import one.util.streamex.StreamEx;

@Log4j2
@ApplicationScoped
public class WaveLinkService extends WaveLinkClient implements IWaveLinkClientEventListener, IFocusRedirector {
    private final SaveService saveService;
    private boolean wasEnabled;

    WaveLinkService() {
        super(false);
        saveService = null;
    }

    @Override
    public boolean handleFocusVolumeRequest(String targetProcess, float volume) {
        var channelId = findChannelIdForFocusApp();
        if (channelId.isPresent()) {
            setChannelLevel(channelId.get(), volume);
            return true;
        }
        return false;
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

    public void settingsChanged() {
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
            return;
        }
        if (isConnected()) {
            log.debug("WaveLink connected, sending ping.");
            ping();
        } else {
            log.info("WaveLink not connected, connecting.");
            reconnect();
        }
    }

    @Override
    public void connectionClosed() {
        log.info("WaveLink connection closed.");
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
