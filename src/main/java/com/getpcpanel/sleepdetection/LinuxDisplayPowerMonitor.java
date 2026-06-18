package com.getpcpanel.sleepdetection;

import java.util.function.Consumer;

import com.getpcpanel.sleepdetection.LinuxX11.LibX11;
import com.getpcpanel.sleepdetection.LinuxX11.LibXext;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.ShortByReference;

import lombok.extern.log4j.Log4j2;

/**
 * Detects the monitors being powered off/on on Linux (X11 DPMS) and reports it as a
 * {@link SystemEventType#displayOff}/{@link SystemEventType#displayOn}. It polls {@code DPMSInfo}
 * (a downcall, native-image safe) rather than waiting on an event, because DPMS exposes no signal.
 *
 * <p>Best-effort and X11-only: if there is no X display (Wayland without XWayland) or the server is
 * not DPMS-capable, the monitor logs once and stays idle. Like all sleep detection, any failure is
 * swallowed — it must never take down the app.
 */
@Log4j2
public class LinuxDisplayPowerMonitor {
    private static final long POLL_INTERVAL_MS = 2_000L;

    private final Consumer<SystemEventType> sink;
    private volatile boolean running;
    private Thread thread;

    public LinuxDisplayPowerMonitor(Consumer<SystemEventType> sink) {
        this.sink = sink;
    }

    public void start() {
        running = true;
        thread = new Thread(this::run, "linux-display-power-monitor");
        thread.setDaemon(true);
        thread.start();
    }

    public void stop() {
        running = false;
        if (thread != null) {
            thread.interrupt();
        }
    }

    private void run() {
        Pointer display = null;
        try {
            display = LibX11.INSTANCE.XOpenDisplay(null);
            if (display == null) {
                log.info("No X display available; Linux display-power detection disabled");
                return;
            }
            if (!LibXext.INSTANCE.DPMSQueryExtension(display, new IntByReference(), new IntByReference())
                    || !LibXext.INSTANCE.DPMSCapable(display)) {
                log.info("X server is not DPMS-capable; Linux display-power detection disabled");
                return;
            }
            log.info("Linux display-power detection started (X11 DPMS)");
            pollLoop(display);
        } catch (Throwable e) { // NOSONAR - display-power detection is non-essential, must never crash the app
            log.warn("Linux display-power detection unavailable: {}", e.toString());
            log.debug("display-power monitor failure", e);
        } finally {
            if (display != null) {
                try {
                    LibX11.INSTANCE.XCloseDisplay(display);
                } catch (Throwable e) { // NOSONAR - best-effort cleanup
                    log.debug("Error closing X display", e);
                }
            }
        }
    }

    private void pollLoop(Pointer display) {
        var powerLevel = new ShortByReference();
        var state = new IntByReference();
        Boolean wasOff = null;
        while (running) {
            try {
                Thread.sleep(POLL_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            if (LibXext.INSTANCE.DPMSInfo(display, powerLevel, state) == 0) {
                continue; // query failed this tick; try again
            }
            // Any level other than "on" (standby/suspend/off) means the screens are dark.
            var off = powerLevel.getValue() != LinuxX11.DPMS_MODE_ON;
            if (wasOff == null || off != wasOff) {
                wasOff = off;
                sink.accept(off ? SystemEventType.displayOff : SystemEventType.displayOn);
            }
        }
    }
}
