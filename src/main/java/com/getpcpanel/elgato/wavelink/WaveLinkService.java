package com.getpcpanel.elgato.wavelink;

import java.net.ConnectException;
import java.util.concurrent.CompletionException;

import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.getpcpanel.profile.SaveService;
import com.getpcpanel.profile.SaveService.SaveEvent;

import dev.niels.elgato.wavelink.IWaveLinkClientEventListener;
import dev.niels.elgato.wavelink.WaveLinkClient;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Service
public class WaveLinkService extends WaveLinkClient implements IWaveLinkClientEventListener {
    private final SaveService saveService;
    private boolean wasEnabled;

    public WaveLinkService(SaveService saveService) {
        super(false);
        this.saveService = saveService;
        wasEnabled = isEnabled();
        addListener(this);
    }

    public boolean isEnabled() {
        return saveService.get().getElgato().waveLinkEnabled();
    }

    @EventListener(SaveEvent.class)
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

    @Scheduled(fixedDelay = 10_000)
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
