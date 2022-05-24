package util;

import hid.DeviceCommunicationHandler;
import hid.DeviceScanner;
import hid.OutputInterpreter;
import javafx.application.Platform;
import javafx.scene.paint.Color;
import lombok.extern.log4j.Log4j2;
import main.Device;
import main.DeviceType;
import main.Window;

import java.io.InputStream;
import java.util.Scanner;

@Log4j2
public class SleepDetector {
    public static void start() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> onSuspended(true),

                "Shutdown Hook Thread"));
        new Thread(() -> {
            ProcessBuilder c = new ProcessBuilder("sndctrl.exe", "sleeplistener");
            c.redirectErrorStream(true);
            try {
                Process proc = c.start();
                InputStream in = proc.getInputStream();
                Scanner scan = new Scanner(in);
                scan.nextLine();
                while (scan.hasNextLine()) {
                    String x = scan.nextLine();
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
            for (Device device : Window.devices.values()) {
                if (device.getDeviceType() != DeviceType.PCPANEL_RGB)
                    continue;
                log.debug("pause: {}", device.getSerialNumber());
                boolean[] bs = new boolean[device.getDeviceType().getAnalogCount()];
                OutputInterpreter.sendRGBAll(device.getSerialNumber(), Color.BLACK, bs, true);
                if (shutdown) {
                    DeviceCommunicationHandler handler = DeviceScanner.CONNECTED_DEVICE_MAP.get(device.getSerialNumber());
                    for (int i = 0; i < 20; i++) {
                        log.debug("{}", i);
                        if (handler.getPriorityQueue().isEmpty())
                            break;
                        try {
                            Thread.sleep(100L);
                        } catch (InterruptedException interruptedException) {
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
            for (Device device : Window.devices.values()) {
                log.info("RESUME: {}", device.getSerialNumber());
                OutputInterpreter.sendLightingConfig(device.getSerialNumber(), device.getDeviceType(), device.getLightingConfig(), true);
            }
        });
    }
}

