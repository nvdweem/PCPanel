package util;

import java.io.InputStream;
import java.util.Scanner;

import hid.DeviceCommunicationHandler;
import hid.DeviceScanner;
import hid.OutputInterpreter;
import javafx.application.Platform;
import javafx.scene.paint.Color;
import main.Device;
import main.DeviceType;
import main.Window;

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
                    if (x.equals("Suspend")) {
                        onSuspended();
                        continue;
                    }
                    if (x.equals("Resume")) {
                        onResumed();
                        continue;
                    }
                    System.err.println("SD ERROR: " + x);
                }
                scan.close();
                in.close();
                proc.destroy();
            } catch (Exception e) {
                e.printStackTrace();
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
                System.err.println("pause: " + device.getSerialNumber());
                boolean[] bs = new boolean[device.getDeviceType().getAnalogCount()];
                OutputInterpreter.sendRGBAll(device.getSerialNumber(), Color.BLACK, bs, true);
                if (shutdown) {
                    DeviceCommunicationHandler handler = DeviceScanner.CONNECTED_DEVICE_MAP.get(device.getSerialNumber());
                    for (int i = 0; i < 20; i++) {
                        System.err.println(i);
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
                System.err.println("RESUME: " + device.getSerialNumber());
                OutputInterpreter.sendLightingConfig(device.getSerialNumber(), device.getDeviceType(), device.getLightingConfig(), true);
            }
        });
    }
}

