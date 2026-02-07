package com.getpcpanel.wavelink;

import java.net.ConnectException;
import java.util.concurrent.CompletionException;

import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.getpcpanel.profile.SaveService;
import com.getpcpanel.profile.SaveService.SaveEvent;

import dev.niels.wavelink.IWaveLinkClient;
import dev.niels.wavelink.IWaveLinkClientEventListener;
import dev.niels.wavelink.WaveLinkClient;
import lombok.experimental.Delegate;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Service
public class WaveLinkService implements IWaveLinkClientEventListener {
    private final SaveService saveService;
    @Delegate private final IWaveLinkClient client = new WaveLinkClient(false);
    private boolean wasEnabled;

    public WaveLinkService(SaveService saveService) {
        this.saveService = saveService;
        wasEnabled = isEnabled();
        client.addListener(this);
    }

    public boolean isEnabled() {
        return saveService.get().getWaveLink().enabled();
    }

    @EventListener(SaveEvent.class)
    public void settingsChanged() {
        var is = isEnabled();
        if (wasEnabled && !is) {
            client.disconnect();
        }
        if (!wasEnabled && is) {
            client.reconnect();
        }
        wasEnabled = isEnabled();
    }

    @Scheduled(fixedDelay = 10_000)
    public void checkConnection() {
        if (!isEnabled()) {
            return;
        }
        if (isConnected()) {
            log.debug("WaveLink connected, sending ping.");
            client.ping();
        } else {
            log.info("WaveLink not connected, connecting.");
            client.reconnect();
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

        if (t instanceof ConnectException ce) {
            log.warn("Unable to connect to WaveLink");
        }
    }
}
