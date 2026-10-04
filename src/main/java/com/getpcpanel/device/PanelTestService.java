package com.getpcpanel.device;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.getpcpanel.device.lightshow.Animations;
import com.getpcpanel.device.provider.pcpanel.DeviceScanner;
import com.getpcpanel.device.lightshow.LightShow;
import com.getpcpanel.profile.SaveService;

import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import lombok.extern.log4j.Log4j2;

/**
 * "Test my panel" and the start-up animation. While a device is being tested its controls do nothing (the settings
 * page follows them through the usual knob/button events), so turning every knob from end to end changes no volume.
 * A test ends when the page says so, or by itself after {@link #MAX_TEST_MS}.
 */
@Log4j2
@Startup
@ApplicationScoped
public class PanelTestService {
    static final long MAX_TEST_MS = 3 * 60_000;

    @Inject DeviceHolder devices;
    @Inject LightShow lightShow;
    @Inject SaveService save;

    /** Devices that are connected and have had their start-up animation; a rebuild (undo, restore) is not a connect. */
    private final Set<String> greeted = ConcurrentHashMap.newKeySet();

    /** Devices under test, with when their test expires. */
    private final Map<String, Long> testing = new ConcurrentHashMap<>();

    public boolean isTesting(String serial) {
        var until = testing.get(serial);
        if (until == null) {
            return false;
        }
        if (System.currentTimeMillis() > until) {
            testing.remove(serial);
            return false;
        }
        return true;
    }

    /** Starts testing a device: controls stop acting, and the lights run through their test sequence. */
    public boolean start(String serial) {
        var device = devices.getDevice(serial).orElse(null);
        if (device == null) {
            return false;
        }
        testing.put(serial, System.currentTimeMillis() + MAX_TEST_MS);
        testLights(serial);
        log.info("Panel test started on {}", serial);
        return true;
    }

    public void testLights(String serial) {
        devices.getDevice(serial)
               .filter(d -> d.descriptor().globalLighting() != null)
               .ifPresent(d -> lightShow.play(d, Animations.selfTest(), Animations.SELF_TEST_MS));
    }

    public void stop(String serial) {
        if (testing.remove(serial) != null) {
            log.info("Panel test ended on {}", serial);
        }
    }

    /** Plays the start-up animation on every connected device. */
    public void playStartupAnimation() {
        devices.all().forEach(this::playStartupAnimation);
    }

    void onConnected(@Observes DeviceHolder.DeviceFullyConnectedEvent event) {
        var first = greeted.add(event.device().getSerialNumber());
        if (first && save.get().isStartupAnimation()) {
            playStartupAnimation(event.device());
        }
    }

    void onDisconnected(@Observes DeviceScanner.DeviceDisconnectedEvent event) {
        greeted.remove(event.serialNum());
    }

    private void playStartupAnimation(Device device) {
        if (device.descriptor().globalLighting() != null) {
            lightShow.play(device, Animations.ignition(), Animations.STARTUP_MS);
        }
    }
}
