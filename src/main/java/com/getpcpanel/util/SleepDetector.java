package com.getpcpanel.util;

import java.util.Scanner;

import com.getpcpanel.Main;
import com.getpcpanel.device.DeviceType;
import com.getpcpanel.hid.DeviceScanner;
import com.getpcpanel.hid.OutputInterpreter;

import javafx.application.Platform;
import javafx.scene.paint.Color;
import lombok.extern.log4j.Log4j2;

@Log4j2
public final class SleepDetector {
    private SleepDetector() {
    }

    public static void start() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> onSuspended(true), "Shutdown Hook Thread"));
        new Thread(() -> {
            var c = new ProcessBuilder("sndctrl.exe", "sleeplistener");
            c.redirectErrorStream(true);
            try {
                var proc = c.start();
                var in = proc.getInputStream();
                var scan = new Scanner(in);
                scan.nextLine();
                while (scan.hasNextLine()) {
                    var x = scan.nextLine();
                    if ("Suspend".equals(x)) {
                        onSuspended();
                        continue;
                    }
                    if ("Resume".equals(x)) {
                        onResumed();
                        continue;
                    }
                    log.error("SD ERROR: {}", x);
                }
                scan.close();
                in.close();
                proc.destroy();
            } catch (Exception e) {
                log.error("Unable to listen to sleep", e);
            }
        }, "Sleep Detector Thread").start();
    }

    private static void onSuspended() {
        onSuspended(false);
    }

    private static void onSuspended(boolean shutdown) {
        Runnable r = () -> {
            for (var device : Main.devices.values()) {
                if (device.getDeviceType() != DeviceType.PCPANEL_RGB)
                    continue;
                log.debug("pause: {}", device.getSerialNumber());
                var bs = new boolean[device.getDeviceType().getAnalogCount()];
                OutputInterpreter.sendRGBAll(device.getSerialNumber(), Color.BLACK, bs, true);
                if (shutdown) {
                    var handler = DeviceScanner.getConnectedDevice(device.getSerialNumber());
                    for (var i = 0; i < 20; i++) {
                        log.debug("{}", i);
                        if (handler.getPriorityQueue().isEmpty())
                            break;
                        try {
                            Thread.sleep(100L);
                        } catch (InterruptedException e) {
                            log.warn("Unable to sleep", e);
                        }
                    }
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

