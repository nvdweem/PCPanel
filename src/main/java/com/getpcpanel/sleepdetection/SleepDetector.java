package com.getpcpanel.sleepdetection;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.getpcpanel.device.Device;
import com.getpcpanel.device.DeviceHolder;
import com.getpcpanel.device.lightshow.LightShow.Layout;
import com.getpcpanel.device.provider.pcpanel.DeviceScanner;
import com.getpcpanel.device.provider.pcpanel.OutputInterpreter;
import com.getpcpanel.integration.device.BrightnessService;
import com.getpcpanel.profile.SaveService;
import com.getpcpanel.profile.dto.LightingConfig;
import com.getpcpanel.profile.dto.LightingConfig.LightingMode;
import com.getpcpanel.profile.dto.SingleKnobLightingConfig.SINGLE_KNOB_MODE;
import com.getpcpanel.profile.dto.SingleLogoLightingConfig.SINGLE_LOGO_MODE;
import com.getpcpanel.profile.dto.SingleSliderLabelLightingConfig.SINGLE_SLIDER_LABEL_MODE;
import com.getpcpanel.profile.dto.SingleSliderLightingConfig.SINGLE_SLIDER_MODE;
import com.getpcpanel.sleepdetection.DarkReasonGate.Reason;
import com.getpcpanel.util.coloroverride.OverrideColorService;
import com.getpcpanel.util.concurrent.AppThreads;

import io.quarkus.runtime.ShutdownEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import lombok.extern.log4j.Log4j2;

@Log4j2
@ApplicationScoped
public final class SleepDetector {
    private static final String BLACK = "#000000";
    private static final LightingConfig ALL_OFF = LightingConfig.createAllColor(BLACK);
    private static final long SHUTDOWN_OFF_TIMEOUT_SECONDS = 10;
    /** The brightness of a dark frame for a device without lighting of its own. */
    private static final int FULL_BRIGHTNESS = 100;
    private static final Object OFF_SENT = new Object();

    /**
     * Every off/relight (and dark frame) runs through this one queue, in the order the gate decided them. That single
     * ordering is the fix for the boot-time half of #145: the off used to run on a freshly spawned
     * thread while the relight ran on the caller's thread, so a quick dark→light pair could deliver
     * the ALL_OFF <em>after</em> the relight and the panels stayed dark until the user touched a
     * lighting setting. It also keeps the (cheap, queue-only) device writes off the callers — the
     * Windows message pump and the lock poller.
     */
    private final ExecutorService lightingWrites;

    /** Collapses the overlapping dark reasons (suspend / lock / display-off) into off/relight transitions. */
    private final DarkReasonGate gate;

    /**
     * Per device, what this detector last sent while dark ({@link #OFF_SENT} or the {@link DarkFrame} shown), so an
     * unchanged one isn't sent again; absent when the panel may show something else. Only touched on
     * {@link #lightingWrites}.
     */
    private final Map<String, Object> darkSent = new ConcurrentHashMap<>();
    /** Devices with a dark frame queued, so a burst of requests sends one frame. */
    private final Set<String> darkQueued = ConcurrentHashMap.newKeySet();
    private volatile boolean shutDown;

    @Inject
    DeviceScanner deviceScanner;
    @Inject
    OutputInterpreter outputInterpreter;
    @Inject
    DeviceHolder devices;
    @Inject
    SaveService saveService;
    @Inject
    OverrideColorService overrideColorService;
    @Inject
    BrightnessService brightnessService;
    @Inject
    Event<PanelsDarkEvent> panelsDark;

    public SleepDetector() {
        this(Executors.newSingleThreadExecutor(AppThreads.factory("sleep-detector", true)));
    }

    SleepDetector(ExecutorService lightingWrites) {
        this.lightingWrites = lightingWrites;
        gate = new DarkReasonGate(this::onDark, this::onResumed, lightingWrites);
    }

    /**
     * Whether the panels are dark for a lock, sleep or screens off; nothing but dark frames ({@link #showsDarkFrames()})
     * should light them up meanwhile.
     */
    public boolean isDark() {
        return gate.isDark();
    }

    /** Whether the panels are dark for a lock and/or screens off only, with the PC awake. */
    public boolean isDarkButAwake() {
        return gate.isDarkButAwake();
    }

    /**
     * Whether the panels show dark frames: they are dark with the PC awake and a feature that keeps showing on dark
     * panels (music visualizer, notification lights) is switched on. Only those features' colour overrides show
     * meanwhile, and lighting sent for a device shows its dark frame instead.
     */
    public boolean showsDarkFrames() {
        if (!gate.isDarkButAwake()) {
            return false;
        }
        var save = saveService.get();
        return save.isVisualizerWhileLocked() || save.isNotificationLightsWhileLocked();
    }

    /**
     * Brings a dark panel's lights up to date, on the lighting queue: while the panels are dark with the PC awake, the
     * dark frame (every light off, the overrides of features that show on dark panels on top) while one of them has
     * something to show on {@code serial}, else all-off. Each is sent only when it differs from what was sent last, or
     * the panel changed meanwhile ({@link #panelChanged}). Asked for after the wake relight or while asleep, nothing is
     * sent.
     */
    public void showDarkFrame(String serial) {
        if (shutDown || !gate.isDarkButAwake() || !darkQueued.add(serial)) {
            return;
        }
        lightingWrites.execute(() -> {
            darkQueued.remove(serial);
            if (shutDown || !gate.isDarkButAwake()) {
                return;
            }
            try {
                devices.getDevice(serial).filter(d -> d.deviceType() != null).ifPresent(d -> darkLighting(d, false));
            } catch (Exception e) {
                log.debug("Unable to show the dark frame on {}", serial, e);
            }
        });
    }

    /**
     * The panel may show something other than what was sent while dark (it was initialised on connect, a temporary
     * frame such as a light show went out), so the next {@link #showDarkFrame} sends in full.
     */
    public void panelChanged(String serial) {
        if (!gate.isDark()) {
            return;
        }
        darkQueued.remove(serial); // a request queued before this one must not swallow the next
        lightingWrites.execute(() -> darkSent.remove(serial));
    }

    public void onShutdown(@Observes ShutdownEvent event) {
        shutDown = true; // no dark frame after the final off
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
            return;
        }
        // A toggle for showing on dark panels may have changed: show, or switch off, what that means now.
        if (gate.isDarkButAwake()) {
            devices.values().forEach(d -> showDarkFrame(d.getSerialNumber()));
        }
    }

    private void onDark(boolean awake) {
        panelsDark.fire(new PanelsDarkEvent(true, awake)); // temporary frames stop before the lights go off
        darkSent.clear();
        if (!awake) {
            allOff(false);
            return;
        }
        for (var device : devices.values()) {
            if (device.deviceType() == null) {
                continue; // Non-PCPanel devices (e.g. Deej) have no HID lighting channel to switch off.
            }
            try {
                darkLighting(device, true);
            } catch (Exception e) {
                log.error("Unable to switch off lighting for {}", device.getSerialNumber(), e);
            }
        }
    }

    /** The dark frame while something shows on {@code device}, else all-off; skipped when sent last, unless {@code force}d. */
    private void darkLighting(Device device, boolean force) {
        var serial = device.getSerialNumber();
        Object target = OFF_SENT;
        var lighting = ALL_OFF;
        if (showsDarkFrames()) {
            var layout = Layout.of(device.descriptor());
            if (overrideColorService.anyOverride(serial, layout)) {
                lighting = darkFrame(device);
                target = shown(serial, layout, lighting);
            }
        }
        if (!force && target.equals(darkSent.get(serial))) {
            return;
        }
        outputInterpreter.sendLightingConfig(serial, device.deviceType(), lighting, true);
        darkSent.put(serial, target); // only once it went out: a failed send is tried again by the next request
    }

    /** What a dark frame shows on the panel: its brightness and the override on each light. */
    private record DarkFrame(int brightness, OptionalInt runtimeBrightness, List<Object> overrides) {
    }

    private DarkFrame shown(String serial, Layout layout, LightingConfig frame) {
        var overrides = new ArrayList<Object>();
        for (var i = 0; i < layout.knobs(); i++) {
            overrides.add(overrideColorService.getDialOverride(serial, i).orElse(null));
        }
        for (var i = 0; i < layout.sliders(); i++) {
            overrides.add(overrideColorService.getSliderOverride(serial, i).orElse(null));
            overrides.add(overrideColorService.getSliderLabelOverride(serial, i).orElse(null));
        }
        overrides.add(overrideColorService.getLogoOverride(serial).orElse(null));
        return new DarkFrame(frame.getGlobalBrightness(), brightnessService.runtimeBrightness(serial), overrides);
    }

    /**
     * Every light black, at the device's brightness (full for a device without lighting of its own), as per-control
     * lighting the overrides paint on. Black rather than unset, so the base layer fills nothing in.
     */
    static LightingConfig darkFrame(Device device) {
        var layout = Layout.of(device.descriptor());
        var frame = new LightingConfig(layout.knobs(), layout.sliders());
        frame.setLightingMode(LightingMode.CUSTOM);
        var own = device.lightingConfig();
        frame.setGlobalBrightness(own != null ? own.getGlobalBrightness() : FULL_BRIGHTNESS);
        for (var knob : frame.knobConfigs()) {
            knob.setMode(SINGLE_KNOB_MODE.STATIC).setColor1(BLACK);
        }
        for (var slider : frame.sliderConfigs()) {
            slider.setMode(SINGLE_SLIDER_MODE.STATIC).setColor1(BLACK);
        }
        for (var label : frame.sliderLabelConfigs()) {
            label.setMode(SINGLE_SLIDER_LABEL_MODE.STATIC).setColor(BLACK);
        }
        frame.logoConfig().setMode(SINGLE_LOGO_MODE.STATIC).setColor(BLACK);
        return frame;
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
            if (handler.isOutputIdle())
                break;
            try {
                Thread.sleep(100L);
            } catch (InterruptedException e) {
                log.warn("Unable to sleep", e);
            }
        }
    }

    private void onResumed() {
        darkSent.clear();
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
        panelsDark.fire(new PanelsDarkEvent(false, true)); // after the relight, so temporary frames paint over it
    }
}
