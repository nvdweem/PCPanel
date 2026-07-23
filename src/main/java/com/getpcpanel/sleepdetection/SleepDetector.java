package com.getpcpanel.sleepdetection;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.getpcpanel.device.Device;
import com.getpcpanel.device.DeviceHolder;
import com.getpcpanel.device.provider.pcpanel.DeviceScanner;
import com.getpcpanel.device.provider.pcpanel.OutputInterpreter;
import com.getpcpanel.profile.SaveService;
import com.getpcpanel.profile.dto.LightingConfig;
import com.getpcpanel.sleepdetection.DarkReasonGate.Reason;
import com.getpcpanel.util.concurrent.AppThreads;

import io.quarkus.runtime.ShutdownEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import lombok.extern.log4j.Log4j2;

@Log4j2
@ApplicationScoped
public final class SleepDetector {
    private static final LightingConfig ALL_OFF = LightingConfig.createAllColor("#000000");
    private static final long SHUTDOWN_OFF_TIMEOUT_SECONDS = 10;

    /**
     * Every off/relight runs through this one queue, in the order the gate decided them. That single
     * ordering is the fix for the boot-time half of #145: the off used to run on a freshly spawned
     * thread while the relight ran on the caller's thread, so a quick dark→light pair could deliver
     * the ALL_OFF <em>after</em> the relight and the panels stayed dark until the user touched a
     * lighting setting. It also keeps the (cheap, queue-only) device writes off the callers — the
     * Windows message pump and the lock poller.
     */
    private final ExecutorService lightingWrites = Executors.newSingleThreadExecutor(AppThreads.factory("sleep-detector", true));

    /** Collapses the overlapping dark reasons (suspend / lock / display-off) into off/relight transitions. */
    private final DarkReasonGate gate = new DarkReasonGate(this::onSuspended, this::onResumed, lightingWrites);

    @Inject
    DeviceScanner deviceScanner;
    @Inject
    OutputInterpreter outputInterpreter;
    @Inject
    DeviceHolder devices;
    @Inject
    SaveService saveService;

    public void onShutdown(@Observes ShutdownEvent event) {
        // Same queue as every other write, so a decided-but-not-yet-sent relight cannot land after
        // this final off. Waiting is required: the app is about to exit and the off must be flushed.
        var done = lightingWrites.submit(() -> allOff(true));
        try {
            done.get(SHUTDOWN_OFF_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.warn("Shutdown lights-off did not complete", e);
        }
    }

    public void onEvent(@Observes SystemEvent event) {
        // Opt-out for machines where the detection misbehaves (#145): leave the lighting alone
        // entirely. Ignoring the light-side events too is deliberate — a stray relight would be just
        // as unasked-for. The lights-off on app shutdown is a separate behavior and stays.
        if (!saveService.get().isSleepDetectionEnabled()) {
            return;
        }
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

    public void onSaveChanged(@Observes SaveService.SaveEvent event) {
        // Switching the feature off while a dark reason is active must not strand dark panels: the
        // events that would have cleared it are ignored from now on, so clear it and relight here.
        if (!saveService.get().isSleepDetectionEnabled()) {
            gate.resetIfDark();
        }
    }

    private void onSuspended() {
        allOff(false);
    }

    private void allOff(boolean shutdown) {
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
            // others their relight.
            try {
                outputInterpreter.sendLightingConfig(device.getSerialNumber(), device.deviceType(), device.lightingConfig(), true);
            } catch (Exception e) {
                log.error("Unable to restore lighting for {}", device.getSerialNumber(), e);
            }
        }
    }
}
