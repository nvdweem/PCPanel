package com.getpcpanel.device.lightshow;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.getpcpanel.device.Device;
import com.getpcpanel.profile.dto.LightingConfig;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.log4j.Log4j2;

/**
 * Plays a short {@link Animation} on a device's lights, then puts back the lighting it showed before. Frames are sent
 * as temporary per-control lighting, never saved. While a show runs, colour overrides (mute colours, notification
 * lights, audio levels) are held back for that device, so they don't paint over the frames. One show per device at a
 * time; a new one replaces the one running.
 */
@Log4j2
@ApplicationScoped
public class LightShow {
    static final long FRAME_MS = 40;

    private final Set<String> running = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<String, Thread> threads = new ConcurrentHashMap<>();

    public boolean isRunning(String serial) {
        return running.contains(serial);
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
        var before = device.lightingConfig();
        var layout = Layout.of(before);
        running.add(serial);
        try {
            var start = System.currentTimeMillis();
            while (!Thread.currentThread().isInterrupted()) {
                var elapsed = System.currentTimeMillis() - start;
                if (elapsed >= durationMs) {
                    break;
                }
                device.setLighting(animation.frame(elapsed / (double) durationMs, layout), true);
                Thread.sleep(FRAME_MS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.warn("Light show on {} stopped early", serial, e);
        } finally {
            running.remove(serial);
            threads.remove(serial, Thread.currentThread());
            try {
                device.setLighting(before, true);
            } catch (Exception e) {
                log.debug("Unable to restore lighting on {}", serial, e);
            }
        }
    }

    /** How many lights a device has, taken from its lighting config. */
    public record Layout(int knobs, int sliders) {
        static Layout of(LightingConfig lc) {
            return new Layout(lc.knobConfigs() == null ? 0 : lc.knobConfigs().length, lc.sliderConfigs() == null ? 0 : lc.sliderConfigs().length);
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
