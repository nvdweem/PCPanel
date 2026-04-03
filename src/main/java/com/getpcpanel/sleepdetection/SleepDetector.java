package com.getpcpanel.sleepdetection;

import jakarta.inject.Inject;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.context.ApplicationScoped;

import com.getpcpanel.device.Device;
import com.getpcpanel.hid.DeviceHolder;
import com.getpcpanel.hid.DeviceScanner;
import com.getpcpanel.hid.OutputInterpreter;
import com.getpcpanel.profile.LightingConfig;

import jakarta.annotation.PostConstruct;
import javafx.application.Platform;
import javafx.scene.paint.Color;
import lombok.RequiredArgsConstructor;
import lombok.extern.jbosslog.JBossLog;

@JBossLog
@ApplicationScoped
public final class SleepDetector {
    private static final LightingConfig ALL_OFF = LightingConfig.createAllColor(Color.BLACK);
    @Inject
    DeviceScanner deviceScanner;
    @Inject
    OutputInterpreter outputInterpreter;
    @Inject
    DeviceHolder devices;

    @PostConstruct
    public void init() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> onSuspended(true), "Shutdown SleepDetector Hook Thread"));
    }

        public void onEvent(@Observes WindowsSystemEventService.WindowsSystemEvent event) {
        switch (event.type()) {
            case goingToSuspend, locked -> onSuspended(false);
            case resumedFromSuspend, unlocked -> onResumed();
        }
    }

    private void onSuspended(boolean shutdown) {
        Runnable r = () -> {
            for (var device : devices.values()) {
                log.debug("Pause: {}", device.getSerialNumber());
                outputInterpreter.sendLightingConfig(device.getSerialNumber(), device.getDeviceType(), ALL_OFF, true);

                if (shutdown) {
                    waitUntilEmptyPrioQueue(device);
                }
            }
            if (shutdown)
                deviceScanner.close();
        };

        if (shutdown) {
            r.run();
        } else {
            Platform.runLater(r);
        }
        log.info("Stopped sleep detector");
    }

    private void waitUntilEmptyPrioQueue(Device device) {
        var handler = deviceScanner.getConnectedDevice(device.getSerialNumber());
        for (var i = 0; i < 20; i++) {
            if (handler.getQueue().isEmpty())
                break;
            try {
                Thread.sleep(100L);
            } catch (InterruptedException e) {
                log.warn("Unable to sleep", e);
            }
        }
    }

    private void onResumed() {
        Platform.runLater(() -> {
            for (var device : devices.values()) {
                log.info("RESUME: {}", device.getSerialNumber());
                outputInterpreter.sendLightingConfig(device.getSerialNumber(), device.getDeviceType(), device.getLightingConfig(), true);
            }
        });
    }
}
