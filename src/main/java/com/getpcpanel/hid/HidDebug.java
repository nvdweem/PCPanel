package com.getpcpanel.hid;

import org.hid4java.HidManager;
import org.hid4java.HidServicesListener;
import org.hid4java.event.HidServicesEvent;

import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;

@Log4j2
public final class HidDebug {
    private HidDebug() {
    }

    @SneakyThrows
    public static void execute() {
        var hidServices = HidManager.getHidServices(DeviceScanner.buildSpecification());
        hidServices.addHidServicesListener(buildListener());
        log.info("Starting HID Debug");
        hidServices.start();
        log.info("HID Debug started");

        log.info("Waiting 10 seconds...");
        Thread.sleep(10_000);
        log.info("Waited 10 seconds, stopping");
    }

    private static HidServicesListener buildListener() {
        return new HidServicesListener() {
            @Override public void hidDeviceAttached(HidServicesEvent event) {
                log.info("Device attached: {}", event);
            }

            @Override public void hidDeviceDetached(HidServicesEvent event) {
                log.info("Device detached: {}", event);
            }

            @Override public void hidFailure(HidServicesEvent event) {
                log.info("Hid failure: {}", event);
            }
        };
    }
}
