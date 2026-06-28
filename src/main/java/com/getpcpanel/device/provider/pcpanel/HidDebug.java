package com.getpcpanel.device.provider.pcpanel;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;

import org.hid4java.HidManager;
import org.hid4java.HidServicesListener;
import org.hid4java.event.HidServicesEvent;

import com.getpcpanel.util.PcPanelRoot;

import lombok.SneakyThrows;

public class HidDebug {
    private final PrintWriter writer;

    @SneakyThrows
    public HidDebug() {
        var outputFile = new File(PcPanelRoot.resolve().toFile(), "hid-debug.txt");
        outputFile.getParentFile().mkdirs();
        writer = new PrintWriter(new FileOutputStream(outputFile));
    }

    @SneakyThrows
    public void execute() {
        var hidServices = HidManager.getHidServices(DeviceScanner.buildSpecification());
        hidServices.addHidServicesListener(buildListener());
        write("Starting HID Debug");
        hidServices.start();
        write("HID Debug started");

        write("Waiting 10 seconds...");
        Thread.sleep(10_000);
        write("Waited 10 seconds, stopping");
        writer.close();
    }

    private HidServicesListener buildListener() {
        return new HidServicesListener() {
            @Override public void hidDeviceAttached(HidServicesEvent event) {
                write("Device attached: " + event);
            }

            @Override public void hidDeviceDetached(HidServicesEvent event) {
                write("Device detached: " + event);
            }

            @Override public void hidFailure(HidServicesEvent event) {
                write("Hid failure: " + event);
            }

            @Override public void hidDataReceived(HidServicesEvent event) {
                write("Data received: " + event);
            }
        };
    }

    private void write(String line) {
        writer.println(line);
        writer.flush();
    }
}
