package com.getpcpanel.wavelink;

import java.net.ConnectException;
import java.util.concurrent.CompletionException;

import com.getpcpanel.profile.SaveService;
import com.getpcpanel.profile.SaveService.SaveEvent;

import dev.niels.wavelink.IWaveLinkClientEventListener;
import dev.niels.wavelink.WaveLinkClient;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import lombok.extern.log4j.Log4j2;

@Log4j2
@ApplicationScoped
public class WaveLinkService extends WaveLinkClient implements IWaveLinkClientEventListener {
    private final SaveService saveService;
    private boolean wasEnabled;

    protected WaveLinkService() {
        super(false);
        saveService = null;
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

    @Scheduled(delay = 10_000, every = "10s")
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
