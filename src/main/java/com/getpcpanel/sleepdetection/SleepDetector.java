package com.getpcpanel.sleepdetection;

import javax.annotation.PostConstruct;

import org.springframework.stereotype.Service;

import com.getpcpanel.device.Device;
import com.getpcpanel.hid.DeviceScanner;
import com.getpcpanel.hid.OutputInterpreter;
import com.getpcpanel.profile.LightingConfig;
import com.getpcpanel.ui.HomePage;

import javafx.application.Platform;
import javafx.scene.paint.Color;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Service
@RequiredArgsConstructor
public final class SleepDetector {
    private static final LightingConfig ALL_OFF = LightingConfig.createAllColor(Color.BLACK);
    private final DeviceScanner deviceScanner;
    private final OutputInterpreter outputInterpreter;

    @PostConstruct
    public void init() {
        Win32SystemMonitor.addListener(new Win32SystemMonitor.IWin32SystemMonitorListener() {
            @Override
            public void onMachineGoingToSuspend() {
                onSuspended(false);
            }

            @Override
            public void onMachineLocked() {
                onSuspended(false);
            }

            @Override
            public void onMachineUnlocked() {
                onResumed();
            }

            @Override
            public void onMachineResumedFromSuspend() {
                onResumed();
            }
        });
        Runtime.getRuntime().addShutdownHook(new Thread(() -> onSuspended(true), "Shutdown SleepDetector Hook Thread"));
    }

    private void onSuspended(boolean shutdown) {
        Runnable r = () -> {
            for (var device : HomePage.devices.values()) {
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
            if (handler.getPriorityQueue().isEmpty())
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
            for (var device : HomePage.devices.values()) {
                log.info("RESUME: {}", device.getSerialNumber());
                outputInterpreter.sendLightingConfig(device.getSerialNumber(), device.getDeviceType(), device.getLightingConfig(), true);
            }
        });
    }
}
