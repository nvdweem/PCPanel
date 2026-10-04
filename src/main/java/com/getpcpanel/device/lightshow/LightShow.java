package com.getpcpanel.device.lightshow;

import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import com.getpcpanel.device.Device;
import com.getpcpanel.device.descriptor.AnalogKind;
import com.getpcpanel.device.descriptor.DeviceDescriptor;
import com.getpcpanel.profile.dto.LightingConfig;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.log4j.Log4j2;

/**
 * Plays a short {@link Animation} on a device's lights, then puts its lighting back. Frames are shown as temporary
 * per-control lighting ({@link Device#showTemporaryLighting}); the device's lighting stays what it was. While a show
 * runs, colour overrides (mute colours, notification lights, audio levels) are held back for that device, so they don't
 * paint over the frames, and the show is the device's {@link Device.LightingPainter}: lighting set meanwhile becomes
 * the device's lighting without being sent, and whatever painted before (notification lights) paints again when the
 * show ends. One show per device at a time; a new one replaces the one running.
 *
 * <p>Others that send temporary frames to a device do so through {@link #ifIdle}, so a show never starts or ends
 * between their check and their send: nothing of theirs reaches the device while one plays.
 */
@Log4j2
@ApplicationScoped
public class LightShow {
    static final long FRAME_MS = 40;

    /** Per device, the thread of the show playing. */
    private final ConcurrentHashMap<String, Thread> running = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Thread> threads = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Object> locks = new ConcurrentHashMap<>();

    public boolean isRunning(String serial) {
        return running.containsKey(serial);
    }

    /** Runs {@code send} unless a show plays on that device, without one starting or ending meanwhile; returns whether it ran. */
    public boolean ifIdle(String serial, Runnable send) {
        synchronized (lock(serial)) {
            if (isRunning(serial)) {
                return false;
            }
            send.run();
            return true;
        }
    }

    private Object lock(String serial) {
        return locks.computeIfAbsent(serial, s -> new Object());
    }

    /** Plays {@code animation} for {@code durationMs} on its own thread. */
    public void play(Device device, Animation animation, long durationMs) {
        var serial = device.getSerialNumber();
        var previous = threads.remove(serial);
        if (previous != null) {
            previous.interrupt();
        }
        var t = new Thread(() -> run(device, animation, durationMs), "light-show-" + serial);
        t.setDaemon(true);
        threads.put(serial, t);
        t.start();
    }

    private void run(Device device, Animation animation, long durationMs) {
        var serial = device.getSerialNumber();
        var me = Thread.currentThread();
        ShowPainter painter;
        synchronized (lock(serial)) {
            running.put(serial, me);
            Device.LightingPainter current;
            do {
                current = device.painter();
                painter = new ShowPainter(current instanceof ShowPainter other ? other.underlying : current);
            } while (!device.replacePainter(current, painter));
        }
        var layout = Layout.of(device.descriptor());
        try {
            var start = System.currentTimeMillis();
            while (!me.isInterrupted()) {
                var elapsed = System.currentTimeMillis() - start;
                if (elapsed >= durationMs) {
                    break;
                }
                device.showTemporaryLighting(animation.frame(elapsed / (double) durationMs, layout));
                Thread.sleep(FRAME_MS);
            }
        } catch (InterruptedException e) {
            me.interrupt();
        } catch (Exception e) {
            log.warn("Light show on {} stopped early", serial, e);
        } finally {
            threads.remove(serial, me);
            synchronized (lock(serial)) {
                if (device.replacePainter(painter, painter.underlying)) { // else a newer show has taken over
                    running.remove(serial, me);
                    try {
                        device.relight();
                    } catch (Exception e) {
                        log.debug("Unable to restore lighting on {}", serial, e);
                    }
                }
            }
        }
    }

    /** Holds lighting set during a show back from the device; the show puts the lighting back when it ends. */
    private record ShowPainter(@Nullable Device.LightingPainter underlying) implements Device.LightingPainter {
        @Override
        public boolean paint(LightingConfig lighting) {
            return true;
        }
    }

    /** How many lights a device has. */
    public record Layout(int knobs, int sliders) {
        /** The knobs and sliders of a device. */
        public static Layout of(DeviceDescriptor descriptor) {
            var inputs = descriptor.analogInputs();
            return new Layout((int) inputs.stream().filter(a -> a.kind() == AnalogKind.KNOB).count(),
                    (int) inputs.stream().filter(a -> a.kind() == AnalogKind.SLIDER).count());
        }

        /** Knobs, then sliders, then the logo. */
        public int count() {
            return knobs + sliders + 1;
        }
    }

    /** One frame per moment of an animation. */
    @FunctionalInterface
    public interface Animation {
        /** The lights at {@code t}, from 0 (start) to 1 (end). */
        LightingConfig frame(double t, Layout layout);
    }
}
