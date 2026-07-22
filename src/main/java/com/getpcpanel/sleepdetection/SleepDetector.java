package com.getpcpanel.sleepdetection;

import com.getpcpanel.device.Device;
import com.getpcpanel.device.DeviceHolder;
import com.getpcpanel.device.provider.pcpanel.DeviceScanner;
import com.getpcpanel.device.provider.pcpanel.OutputInterpreter;
import com.getpcpanel.profile.dto.LightingConfig;
import com.getpcpanel.sleepdetection.DarkReasonGate.Reason;

import io.quarkus.runtime.ShutdownEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import lombok.extern.log4j.Log4j2;

@Log4j2
@ApplicationScoped
public final class SleepDetector {
    private static final LightingConfig ALL_OFF = LightingConfig.createAllColor("#000000");

    /** Collapses the overlapping dark reasons (suspend / lock / display-off) into off/relight transitions. */
    private final DarkReasonGate gate = new DarkReasonGate(() -> onSuspended(false), this::onResumed);

    @Inject
    DeviceScanner deviceScanner;
    @Inject
    OutputInterpreter outputInterpreter;
    @Inject
    DeviceHolder devices;

    public void onShutdown(@Observes ShutdownEvent event) {
        onSuspended(true);
    }

    public void onEvent(@Observes SystemEvent event) {
        switch (event.type()) {
            case goingToSuspend -> gate.add(Reason.suspend);
            case locked -> gate.add(Reason.lock);
            case displayOff -> gate.add(Reason.display);
            case unlocked -> gate.clear(Reason.lock);
            case displayOn -> gate.clear(Reason.display);
            // A resume means the whole machine is awake again: clear every reason and relight, even
            // on platforms whose callback-free detection never saw the matching goingToSuspend.
            case resumedFromSuspend -> gate.reset();
            case logon, logoff -> { /* no lighting action */ }
        }
    }

    private void onSuspended(boolean shutdown) {
        Runnable r = () -> {
            for (var device : devices.values()) {
                if (device.deviceType() == null) {
                    continue; // Non-PCPanel devices (e.g. Deej) have no HID lighting channel to switch off.
                }
                log.debug("Pause: {}", device.getSerialNumber());
                try {
                    outputInterpreter.sendLightingConfig(device.getSerialNumber(), device.deviceType(), ALL_OFF, true);
                    if (shutdown) {
                        waitUntilEmptyPrioQueue(device);
                    }
                } catch (Exception e) {
                    log.error("Unable to switch off lighting for {}", device.getSerialNumber(), e);
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
            if (device.deviceType() == null) {
                continue; // Non-PCPanel devices (e.g. Deej) have no HID lighting channel to restore.
            }
            log.info("RESUME: {}", device.getSerialNumber());
            // A relight that is skipped is not retried by anything: the panel then stays dark until the
            // device reconnects or the user edits lighting. So one device failing must not cost the
            // others their relight, nor unwind onto the caller's thread (the lock poller / message pump).
            try {
                outputInterpreter.sendLightingConfig(device.getSerialNumber(), device.deviceType(), device.lightingConfig(), true);
            } catch (Exception e) {
                log.error("Unable to restore lighting for {}", device.getSerialNumber(), e);
            }
        }
    }
}
