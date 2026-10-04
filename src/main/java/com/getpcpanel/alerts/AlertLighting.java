package com.getpcpanel.alerts;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import com.getpcpanel.device.Device;
import com.getpcpanel.device.lightshow.LightShow;
import com.getpcpanel.device.lightshow.LightShow.Layout;
import com.getpcpanel.device.lightshow.SoftwareAnimation;
import com.getpcpanel.integration.visualizer.VisualizerService;
import com.getpcpanel.profile.dto.LightingConfig;
import com.getpcpanel.profile.dto.LightingConfig.LightingMode;
import com.getpcpanel.sleepdetection.PanelsDarkEvent;

import io.quarkus.runtime.ShutdownEvent;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import lombok.extern.log4j.Log4j2;

/**
 * Lets notification lights show over whole-panel lighting. Colour overrides only show in per-control lighting, so
 * while an alert is lit on a device with solid, per-light, rainbow, wave or breath lighting, that lighting is drawn
 * in software ({@link SoftwareAnimation}) and shown as temporary per-control frames
 * ({@link Device#showTemporaryLighting}), 20 a second while it moves, with the overrides painted on top. Once no alert
 * is lit, the device's own lighting is sent again and the panel animates it itself.
 *
 * <p>The device's lighting is never replaced by a frame. While drawing, the driver is the device's
 * {@link Device.LightingPainter}: lighting sent by anything else (a relight, a brightness change, a profile switch)
 * is drawn straight away instead of reaching the panel, or ends the drawing if it is per-control lighting. A
 * {@link LightShow} wins: nothing is drawn while one plays, and drawing resumes when it ends. Nothing is drawn while
 * the panels are dark for sleep, lock or displays off, and drawing stops before the lights-off at shutdown. Nor while
 * the music visualizer shows on the device: it already sends whole-panel lighting as per-control lighting, which shows
 * the alert above its own colours. One thread per device that is being drawn.
 */
@Log4j2
@ApplicationScoped
public class AlertLighting {
    static final long FRAME_MS = 50;

    @Inject LightShow lightShow;
    @Inject VisualizerService visualizer;

    /** Whether the visualizer shows on a device right now. */
    Predicate<String> visualizing = serial -> visualizer != null && visualizer.isShowing(serial);

    private final Map<String, Driver> drivers = new ConcurrentHashMap<>();
    private volatile boolean dark;
    private volatile boolean shutDown;

    /** Draws {@code device}'s lighting in software while {@code anyAlertLit} and its lighting needs it; hands back otherwise. */
    public void update(Device device, boolean anyAlertLit) {
        if (shutDown) {
            return;
        }
        var serial = device.getSerialNumber();
        var driver = drivers.get(serial);
        if (driver != null && driver.device != device) { // reconnected: the old device object is gone
            driver.abandon();
            driver = null;
        }
        if (!anyAlertLit) {
            if (driver != null) {
                driver.release();
            }
            return;
        }
        if (driver != null && driver.active() || dark || !drawable(device) || lightShow.isRunning(serial) || visualizing.test(serial)) {
            return;
        }
        var started = new Driver(device);
        if (started.begin()) {
            drivers.put(serial, started);
            started.thread.start();
        }
    }

    /** Stops drawing for devices other than {@code serials}, without sending anything (they are gone). */
    public void retain(Set<String> serials) {
        drivers.values().stream().filter(d -> !serials.contains(d.serial)).forEach(Driver::abandon);
    }

    void onPanelsDark(@Observes PanelsDarkEvent event) {
        dark = event.dark();
        drivers.values().forEach(event.dark() ? Driver::pause : Driver::resume);
    }

    /** Before the lights-off at shutdown ({@code SleepDetector}), so no frame lands after it. */
    void onShutdown(@Observes @Priority(1) ShutdownEvent event) {
        shutDown = true;
        stopAll();
    }

    @PreDestroy
    void stopAll() {
        drivers.values().forEach(Driver::abandon);
    }

    private static boolean drawable(Device device) {
        return device.deviceType() != null && device.descriptor().globalLighting() != null && needsDrawing(device.lightingConfig());
    }

    private static boolean needsDrawing(@Nullable LightingConfig lc) {
        return lc != null && lc.lightingMode() != null && lc.lightingMode() != LightingMode.CUSTOM;
    }

    /**
     * Draws one device. Lock order: the light show's lock (through {@link LightShow#ifIdle}), then this driver's; a
     * paint can come in holding the light show's lock (a show ending) and takes only this driver's.
     */
    private final class Driver implements Device.LightingPainter {
        private final Device device;
        private final String serial;
        private final Layout layout;
        private final Thread thread;
        /** The lighting being drawn: the device's own. */
        private LightingConfig base;
        /** Whether the panel shows a frame of {@link #base}; a still lighting is only sent again when it doesn't. */
        private boolean painted;
        private boolean finished;

        Driver(Device device) {
            this.device = device;
            serial = device.getSerialNumber();
            layout = Layout.of(device.descriptor());
            base = device.lightingConfig();
            thread = new Thread(this::run, "notification-lights-" + serial);
            thread.setDaemon(true);
        }

        /** Takes over the device's lighting and shows the first frame now; false when it could not (a show started). */
        boolean begin() {
            return lightShow.ifIdle(serial, () -> {
                synchronized (this) {
                    Device.LightingPainter current;
                    do {
                        current = device.painter();
                    } while (!device.replacePainter(current, this));
                    send(System.currentTimeMillis());
                }
            });
        }

        synchronized boolean active() {
            return !finished;
        }

        /** Stops drawing and sends the device's own lighting again (held back while dark: the relight sends it then). */
        synchronized void release() {
            if (finished) {
                return;
            }
            finish();
            if (!dark) {
                try {
                    device.relight();
                } catch (Exception e) {
                    log.debug("Unable to hand the lighting back on {}", serial, e);
                }
            }
        }

        /** Stops without sending anything. */
        synchronized void abandon() {
            if (!finished) {
                finish();
            }
        }

        /** Waits for a frame being sent to go out; nothing more is sent while {@link #dark}. */
        synchronized void pause() {
            painted = false;
        }

        synchronized void resume() {
            painted = false;
            LockSupport.unpark(thread);
        }

        private void finish() {
            finished = true;
            device.replacePainter(this, null);
            drivers.remove(serial, this);
            LockSupport.unpark(thread);
        }

        @Override
        public synchronized boolean paint(LightingConfig lighting) {
            if (finished) {
                return false;
            }
            if (!needsDrawing(lighting) || visualizing.test(serial)) {
                finish(); // per-control lighting, or the visualizer's, shows the overrides itself
                return false;
            }
            base = lighting; // also while dark: it is what is drawn after the wake
            if (dark) {
                return false;
            }
            send(System.currentTimeMillis());
            return true;
        }

        private void run() {
            while (active()) {
                try {
                    lightShow.ifIdle(serial, this::step);
                } catch (Exception e) {
                    log.debug("Unable to draw notification lights on {}", serial, e);
                }
                LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(FRAME_MS));
            }
        }

        private synchronized void step() {
            if (finished || dark) {
                return;
            }
            if (visualizing.test(serial)) {
                release(); // normally its first relight already ended the drawing (paint)
                return;
            }
            if (!painted || !SoftwareAnimation.isStill(base.lightingMode())) {
                send(System.currentTimeMillis());
            }
        }

        private void send(long now) {
            device.showTemporaryLighting(SoftwareAnimation.frame(base, layout, now));
            painted = true;
        }
    }
}
