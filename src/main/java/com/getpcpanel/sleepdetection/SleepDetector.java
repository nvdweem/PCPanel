package com.getpcpanel.sleepdetection;

import com.getpcpanel.Main;
import com.getpcpanel.device.Device;
import com.getpcpanel.hid.DeviceScanner;
import com.getpcpanel.hid.OutputInterpreter;
import com.getpcpanel.profile.LightingConfig;

import javafx.application.Platform;
import javafx.scene.paint.Color;
import lombok.extern.log4j.Log4j2;

@Log4j2
public final class SleepDetector {
    private static final LightingConfig ALL_OFF = LightingConfig.createAllColor(Color.BLACK);

    private SleepDetector() {
    }

    public static void init() {
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

    private static void onSuspended(boolean shutdown) {
        Runnable r = () -> {
            for (var device : Main.devices.values()) {
                log.debug("pause: {}", device.getSerialNumber());
                OutputInterpreter.sendLightingConfig(device.getSerialNumber(), device.getDeviceType(), ALL_OFF, true);

                if (shutdown) {
                    waitUntilEmptyPrioQueue(device);
                }
            }
            if (shutdown)
                DeviceScanner.close();
        };

        if (shutdown) {
            r.run();
        } else {
            Platform.runLater(r);
        }
        log.info("Stopped sleep detector");
    }

    private static void waitUntilEmptyPrioQueue(Device device) {
        var handler = DeviceScanner.getConnectedDevice(device.getSerialNumber());
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

    private static void onResumed() {
        Platform.runLater(() -> {
            for (var device : Main.devices.values()) {
                log.info("RESUME: {}", device.getSerialNumber());
                OutputInterpreter.sendLightingConfig(device.getSerialNumber(), device.getDeviceType(), device.getLightingConfig(), true);
            }
        });
    }
}
