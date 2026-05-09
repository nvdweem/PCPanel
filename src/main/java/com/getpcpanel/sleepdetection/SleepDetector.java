package com.getpcpanel.sleepdetection;

import com.getpcpanel.device.Device;
import com.getpcpanel.hid.DeviceHolder;
import com.getpcpanel.hid.DeviceScanner;
import com.getpcpanel.hid.OutputInterpreter;
import com.getpcpanel.profile.dto.LightingConfig;
import com.getpcpanel.sleepdetection.WindowsSystemEventService.WindowsSystemEvent;

import io.quarkus.runtime.ShutdownEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import lombok.extern.log4j.Log4j2;

@Log4j2
@ApplicationScoped
public final class SleepDetector {
    private static final LightingConfig ALL_OFF = LightingConfig.createAllColor("#000000");

    @Inject
    DeviceScanner deviceScanner;
    @Inject
    OutputInterpreter outputInterpreter;
    @Inject
    DeviceHolder devices;

    public void onShutdown(@Observes ShutdownEvent event) {
        onSuspended(true);
    }

    public void onEvent(@Observes WindowsSystemEvent event) {
        switch (event.type()) {
            case goingToSuspend, locked -> onSuspended(false);
            case resumedFromSuspend, unlocked -> onResumed();
        }
    }

    private void onSuspended(boolean shutdown) {
        Runnable r = () -> {
            for (var device : devices.values()) {
                log.debug("Pause: {}", device.getSerialNumber());
                outputInterpreter.sendLightingConfig(device.getSerialNumber(), device.deviceType(), ALL_OFF, true);
                if (shutdown) {
                    waitUntilEmptyPrioQueue(device);
                }
            }
        };

        if (shutdown) {
            r.run();
        } else {
            new Thread(r, "SleepDetector-suspend").start();
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
        for (var device : devices.values()) {
            log.info("RESUME: {}", device.getSerialNumber());
            outputInterpreter.sendLightingConfig(device.getSerialNumber(), device.deviceType(), device.lightingConfig(), true);
        }
    }
}
