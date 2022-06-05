package com.getpcpanel.obs;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.springframework.stereotype.Service;

import com.getpcpanel.obs.remote.OBSRemoteController;
import com.getpcpanel.profile.SaveService;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Service
@RequiredArgsConstructor
public class OBSListener extends Thread {
    private static final long SLEEP_DURATION = 1000L;
    private final SaveService saveService;
    private final OBS obs;
    private volatile boolean check;
    private volatile boolean running = true;

    @PostConstruct
    public void init() {
        setName("OBS Listener Thread");
        start();
    }

    @PreDestroy
    public void doStop() {
        running = false;
    }

    @Override
    public void run() {
        while (running) {
            sleep();
            synchronized (OBS.OBSMutex) {
                var save = saveService.get();
                if (!save.isObsEnabled()) {
                    if (obs.controller != null && obs.controller.isConnected()) {
                        log.info("OBS Disconnected");
                        obs.controller.disconnect();
                        obs.controller = null;
                    }
                    continue;
                }
                if (obs.controller == null) {
                    obs.controller = new OBSRemoteController(save.getObsAddress(), save.getObsPort(), save.getObsPassword());
                } else if (check) {
                    obs.controller.disconnect();
                    obs.controller = new OBSRemoteController(save.getObsAddress(), save.getObsPort(), save.getObsPassword());
                } else if (!obs.controller.isConnected()) {
                    obs.controller = new OBSRemoteController(save.getObsAddress(), save.getObsPort(), save.getObsPassword());
                }
                check = false;
            }
        }
    }

    private void sleep() {
        try {
            Thread.sleep(SLEEP_DURATION);
        } catch (InterruptedException e) {
            log.error("OBS Listener Thread was interrupted", e);
        }
    }

    public void check() {
        check = true;
    }
}
