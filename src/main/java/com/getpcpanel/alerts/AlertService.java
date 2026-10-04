package com.getpcpanel.alerts;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

import javax.annotation.Nullable;


import com.getpcpanel.device.Device;
import com.getpcpanel.device.DeviceHolder;
import com.getpcpanel.device.lightshow.LightShow.Layout;
import com.getpcpanel.profile.SaveService;
import com.getpcpanel.profile.WindowFocusChangedEvent;
import com.getpcpanel.profile.dto.NotificationAlert.AlertTrigger;
import com.getpcpanel.profile.dto.SingleKnobLightingConfig;
import com.getpcpanel.profile.dto.SingleKnobLightingConfig.SINGLE_KNOB_MODE;
import com.getpcpanel.profile.dto.SingleLogoLightingConfig;
import com.getpcpanel.profile.dto.SingleLogoLightingConfig.SINGLE_LOGO_MODE;
import com.getpcpanel.profile.dto.SingleSliderLightingConfig;
import com.getpcpanel.profile.dto.SingleSliderLightingConfig.SINGLE_SLIDER_MODE;
import com.getpcpanel.rest.EventBroadcaster.VisualColorsChangedEvent;
import com.getpcpanel.sleepdetection.PanelsDarkEvent;
import com.getpcpanel.sleepdetection.SleepDetector;
import com.getpcpanel.util.coloroverride.ColorOverrideHolder;
import com.getpcpanel.util.coloroverride.IOverrideColorProvider;
import com.getpcpanel.util.coloroverride.IOverrideColorProviderProvider;

import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import lombok.extern.log4j.Log4j2;

/**
 * Notification lights ({@link com.getpcpanel.profile.dto.NotificationAlert}): a knob, slider or the logo lights up,
 * blinks or pulses while an app flashes its taskbar button, shows a notification, uses the microphone or has a window
 * title that matches, or for a few seconds on request ({@link #preview}). Shown as a colour override above the mute
 * colours, on every device and profile, at the alert's own brightness when it has one; over whole-panel lighting {@link AlertLighting} draws that lighting as
 * per-control frames while an alert is lit, so the override shows there too. Nothing is sent to a panel while the panels
 * are dark ({@link PanelsDarkEvent}), except, with {@code Save.notificationLightsWhileLocked} on and the PC awake, the
 * sleep detector's dark frame with the lit lights on it ({@link SleepDetector#showDarkFrame}); nor once the app shuts
 * down.
 *
 * <p>Window titles are read on a thread of their own, once a second while a window-title light is switched on, so a
 * slow look at the windows never holds up a blink or pulse.
 */
@Log4j2
@Startup
@Priority(50)
@ApplicationScoped
public class AlertService implements IOverrideColorProviderProvider {
    /** How often a pulsing light's colours go to the UI at most (the panel itself gets every frame). */
    private static final long PULSE_UI_MS = 200;
    private static final long PREVIEW_MS = 5_000;
    private static final long MIC_POLL_MS = 1_000;
    private static final long NOTIFICATION_POLL_MS = 2_000;
    private static final long TITLE_POLL_MS = 1_000;

    @Inject SaveService save;
    @Inject DeviceHolder devices;
    @Inject MicUsage micUsage;
    @Inject NotificationWatch notificationWatch;
    @Inject WindowTitles windowTitles;
    @Inject AlertLighting alertLighting;
    @Inject SleepDetector sleep;
    @Inject Event<VisualColorsChangedEvent> visualColorsChanged;
    @Inject Event<AlertsLitEvent> alertsLit;

    private final AlertState state = new AlertState();
    private final ColorOverrideHolder holder = new ColorOverrideHolder();
    private volatile boolean running;
    private volatile boolean dark;
    /** While {@link #dark}, whether the PC is awake (locked or screens off, not asleep). */
    private volatile boolean awake = true;
    private volatile boolean shutDown;
    /** Held for a whole tick, so the shutdown observer can wait for one in progress. */
    private final Object ticking = new Object();
    @Nullable private volatile Thread thread;
    @Nullable private volatile Thread titleThread;
    /** The last failure reading window titles, reported once at warning level; null after a good read. */
    @Nullable String titleFailure;
    private long micPolledAt;
    private long notificationsPolledAt;
    private Map<String, String> shown = Map.of();
    private Map<String, Integer> shownBrightness = Map.of();
    private Set<Integer> shownIndexes = Set.of();
    /** Per device serial, when the UI was last told its colours changed. */
    private final Map<String, Long> uiNotifiedAt = new HashMap<>();

    @PostConstruct
    void start() {
        running = true;
        thread = new Thread(this::run, "notification-lights");
        thread.setDaemon(true);
        thread.start();
        titleThread = new Thread(this::pollTitles, "notification-lights-titles");
        titleThread.setDaemon(true);
        titleThread.start();
    }

    @PreDestroy
    void stop() {
        running = false;
        if (thread != null) {
            thread.interrupt();
        }
        if (titleThread != null) {
            titleThread.interrupt();
        }
    }

    /**
     * While the panels are dark the overrides still follow the alerts and the wake relight shows them; meanwhile only a
     * dark frame is sent, when they show on dark panels.
     */
    void onPanelsDark(@Observes PanelsDarkEvent event) {
        awake = event.awake();
        dark = event.dark();
    }

    /** Before the lights-off at shutdown ({@code SleepDetector}): waits for a tick in progress, then sends nothing more. */
    void onShutdown(@Observes @Priority(1) ShutdownEvent event) {
        synchronized (ticking) {
            shutDown = true;
        }
        stop();
    }

    @Override
    public IOverrideColorProvider getOverrideColorProvider() {
        return holder;
    }

    @Override
    public boolean showsWhileDark() {
        return save.get().isNotificationLightsWhileLocked();
    }

    void onFlash(@Observes TaskbarFlashEvent event) {
        state.configure(save.get().getNotificationAlerts());
        state.onFlash(event.exe(), System.currentTimeMillis());
    }

    void onFocus(@Observes WindowFocusChangedEvent event) {
        state.onFocus(event.application());
    }

    /** Notification senders seen, for picking an alert's source. */
    public Set<String> notificationSources() {
        return notificationWatch.sources();
    }

    /** Shows the saved alert at {@code index} for a few seconds; false when there is no such alert. */
    public boolean preview(int index) {
        var configured = save.get().getNotificationAlerts();
        if (index < 0 || index >= configured.size()) {
            return false;
        }
        state.configure(configured);
        state.preview(index, System.currentTimeMillis() + PREVIEW_MS);
        var t = thread;
        if (t != null) {
            LockSupport.unpark(t); // show it now rather than at the next tick
        }
        return true;
    }

    private void run() {
        while (running) {
            var wait = AlertState.TICK_MS;
            try {
                wait = tick(System.currentTimeMillis());
            } catch (Throwable t) {
                log.warn("Notification lights failed to update", t);
            }
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(wait));
        }
    }

    private void pollTitles() {
        var reading = false;
        while (running) {
            reading = pollTitlesOnce(reading);
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(TITLE_POLL_MS));
        }
    }

    /**
     * Reads the window titles while a window-title light is switched on, and lets go once none is. A failure is
     * logged, at warning level once per distinct failure; nothing is thrown. Returns whether titles are being read.
     */
    boolean pollTitlesOnce(boolean reading) {
        try {
            if (state.hasTrigger(AlertTrigger.WINDOW_TITLE)) {
                state.onTitles(windowTitles.titles(), System.currentTimeMillis());
                titleFailure = null;
                return true;
            }
            if (reading) {
                windowTitles.release();
            }
            return false;
        } catch (Throwable t) {
            if (t.toString().equals(titleFailure)) {
                log.debug("Unable to read window titles for notification lights", t);
            } else {
                titleFailure = t.toString();
                log.warn("Unable to read window titles for notification lights", t);
            }
            return reading || state.hasTrigger(AlertTrigger.WINDOW_TITLE);
        }
    }

    /** Brings the lights up to date; returns how many milliseconds until they need it again. */
    long tick(long now) {
        synchronized (ticking) {
            return shutDown ? AlertState.TICK_MS : update(now);
        }
    }

    private long update(long now) {
        state.configure(save.get().getNotificationAlerts());
        if (now - micPolledAt >= MIC_POLL_MS) {
            micPolledAt = now;
            state.onMicUsers(state.hasTrigger(AlertTrigger.MIC_IN_USE) ? micUsage.appsUsingMic() : Set.of(), now);
        }
        if (now - notificationsPolledAt >= NOTIFICATION_POLL_MS) {
            notificationsPolledAt = now;
            if (state.hasTrigger(AlertTrigger.NOTIFICATION)) {
                state.onNotifications(notificationWatch.newestNotifications(), now);
            } else {
                state.forgetNotifications();
            }
        }
        var frame = state.frame(now);
        if (!frame.indexes().equals(shownIndexes)) {
            shownIndexes = frame.indexes();
            alertsLit.fire(new AlertsLitEvent(List.copyOf(frame.indexes())));
        }
        var lit = frame.colors();
        var brightness = frame.brightness();
        var litChanged = !lit.equals(shown) || !brightness.equals(shownBrightness);
        shown = lit;
        shownBrightness = brightness;
        var serials = new HashSet<String>();
        for (var device : devices.all()) {
            serials.add(device.getSerialNumber());
            var changed = litChanged && apply(device, lit, brightness);
            try {
                alertLighting.update(device, litOn(device, frame.targets()));
            } catch (Exception e) {
                log.debug("Unable to draw notification lights over the lighting of {}", device.getSerialNumber(), e);
            }
            if (changed) {
                relight(device, now, frame.pulsing());
            }
        }
        alertLighting.retain(serials);
        return frame.nextTickMs();
    }

    /** Whether one of {@code targets} is a light {@code device} has. */
    static boolean litOn(Device device, Set<String> targets) {
        if (targets.isEmpty()) {
            return false;
        }
        var layout = Layout.of(device.descriptor());
        var type = device.deviceType();
        for (var target : targets) {
            if ("logo".equals(target) ? type != null && type.isHasLogoLed() : lightIndexIn(target, "knob:", layout.knobs()) || lightIndexIn(target, "slider:", layout.sliders())) {
                return true;
            }
        }
        return false;
    }

    private static boolean lightIndexIn(String target, String prefix, int count) {
        if (!target.startsWith(prefix)) {
            return false;
        }
        try {
            var i = Integer.parseInt(target.substring(prefix.length()));
            return i >= 0 && i < count;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Sets this device's overrides to {@code lit}, each light at its alert's own brightness when {@code brightness} has
     * one for it; returns whether anything changed.
     */
    private boolean apply(Device device, Map<String, String> lit, Map<String, Integer> brightness) {
        var serial = device.getSerialNumber();
        var changed = false;
        var knobs = device.descriptor().analogInputs().size();
        for (var i = 0; i < knobs; i++) {
            changed |= setDial(serial, i, lit.get("knob:" + i), brightness.get("knob:" + i));
            changed |= setSlider(serial, i, lit.get("slider:" + i), brightness.get("slider:" + i));
        }
        var logo = lit.get("logo");
        var logoBrightness = brightness.get("logo");
        var current = holder.getLogoOverride(serial);
        if (!Objects.equals(current.map(SingleLogoLightingConfig::getColor).orElse(null), logo)
                || !Objects.equals(current.map(SingleLogoLightingConfig::getOverrideBrightness).orElse(null), logo == null ? null : logoBrightness)) {
            holder.setLogoOverride(serial, logo == null ? null : new SingleLogoLightingConfig().setMode(SINGLE_LOGO_MODE.STATIC).setColor(logo).setOverrideBrightness(logoBrightness));
            changed = true;
        }
        return changed;
    }

    private boolean setDial(String serial, int idx, @Nullable String color, @Nullable Integer brightness) {
        var current = holder.getDialOverride(serial, idx);
        if (Objects.equals(current.map(SingleKnobLightingConfig::getColor1).orElse(null), color)
                && Objects.equals(current.map(SingleKnobLightingConfig::getOverrideBrightness).orElse(null), color == null ? null : brightness)) {
            return false;
        }
        holder.setDialOverride(serial, idx, color == null ? null
                : new SingleKnobLightingConfig().setMode(SINGLE_KNOB_MODE.STATIC).setColor1(color).setOverrideBrightness(brightness));
        return true;
    }

    private boolean setSlider(String serial, int idx, @Nullable String color, @Nullable Integer brightness) {
        var current = holder.getSliderOverride(serial, idx);
        if (Objects.equals(current.map(SingleSliderLightingConfig::getColor1).orElse(null), color)
                && Objects.equals(current.map(SingleSliderLightingConfig::getOverrideBrightness).orElse(null), color == null ? null : brightness)) {
            return false;
        }
        holder.setSliderOverride(serial, idx, color == null ? null
                : new SingleSliderLightingConfig().setMode(SINGLE_SLIDER_MODE.STATIC).setColor1(color).setOverrideBrightness(brightness));
        return true;
    }

    /**
     * Sends the lights to the panel and tells the UI. While a light pulses the panel gets every frame but the UI
     * at most every {@link #PULSE_UI_MS}; the frame that ends the pulse always reaches it. While the panels are dark
     * only the dark frame is sent, and only while they show on dark panels.
     */
    private void relight(Device device, long now, boolean pulsing) {
        if (!dark) {
            try {
                device.relight();
            } catch (Exception e) {
                log.debug("Unable to re-send notification lights for {}", device.getSerialNumber(), e);
                return;
            }
        } else if (awake && showsWhileDark()) {
            sleep.showDarkFrame(device.getSerialNumber());
        }
        var serial = device.getSerialNumber();
        var last = uiNotifiedAt.get(serial);
        if (!pulsing || last == null || now - last >= PULSE_UI_MS) {
            uiNotifiedAt.put(serial, now);
            visualColorsChanged.fire(new VisualColorsChangedEvent(serial));
        }
    }
}
