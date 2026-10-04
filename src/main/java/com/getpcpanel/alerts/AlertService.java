package com.getpcpanel.alerts;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

import javax.annotation.Nullable;


import com.getpcpanel.device.Device;
import com.getpcpanel.device.DeviceHolder;
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
import com.getpcpanel.util.coloroverride.ColorOverrideHolder;
import com.getpcpanel.util.coloroverride.IOverrideColorProvider;
import com.getpcpanel.util.coloroverride.IOverrideColorProviderProvider;

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
 * Notification lights ({@link com.getpcpanel.profile.dto.NotificationAlert}): a knob, slider or the logo lights up
 * blinks or pulses while an app flashes its taskbar button, shows a notification, uses the microphone or has a window
 * title that matches, or for a few seconds on request ({@link #preview}). Shown as a colour override above the mute
 * colours, on every device and profile, in per-control lighting.
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
    @Inject Event<VisualColorsChangedEvent> visualColorsChanged;
    @Inject Event<AlertsLitEvent> alertsLit;

    private final AlertState state = new AlertState();
    private final ColorOverrideHolder holder = new ColorOverrideHolder();
    private volatile boolean running;
    @Nullable private volatile Thread thread;
    @Nullable private volatile Thread titleThread;
    /** The last failure reading window titles, reported once at warning level; null after a good read. */
    @Nullable String titleFailure;
    private long micPolledAt;
    private long notificationsPolledAt;
    private Map<String, String> shown = Map.of();
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

    @Override
    public IOverrideColorProvider getOverrideColorProvider() {
        return holder;
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
    private long tick(long now) {
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
        if (!lit.equals(shown)) {
            shown = lit;
            for (var device : devices.all()) {
                if (apply(device, lit)) {
                    relight(device, now, frame.pulsing());
                }
            }
        }
        return frame.nextTickMs();
    }

    /** Sets this device's overrides to {@code lit}; returns whether anything changed. */
    private boolean apply(Device device, Map<String, String> lit) {
        var serial = device.getSerialNumber();
        var changed = false;
        var knobs = device.descriptor().analogInputs().size();
        for (var i = 0; i < knobs; i++) {
            changed |= setDial(serial, i, lit.get("knob:" + i));
            changed |= setSlider(serial, i, lit.get("slider:" + i));
        }
        var logo = lit.get("logo");
        var currentLogo = holder.getLogoOverride(serial).map(SingleLogoLightingConfig::getColor).orElse(null);
        if (!Objects.equals(currentLogo, logo)) {
            holder.setLogoOverride(serial, logo == null ? null : new SingleLogoLightingConfig().setMode(SINGLE_LOGO_MODE.STATIC).setColor(logo));
            changed = true;
        }
        return changed;
    }

    private boolean setDial(String serial, int idx, @Nullable String color) {
        var current = holder.getDialOverride(serial, idx).map(SingleKnobLightingConfig::getColor1).orElse(null);
        if (Objects.equals(current, color)) {
            return false;
        }
        holder.setDialOverride(serial, idx, color == null ? null : new SingleKnobLightingConfig().setMode(SINGLE_KNOB_MODE.STATIC).setColor1(color));
        return true;
    }

    private boolean setSlider(String serial, int idx, @Nullable String color) {
        var current = holder.getSliderOverride(serial, idx).map(SingleSliderLightingConfig::getColor1).orElse(null);
        if (Objects.equals(current, color)) {
            return false;
        }
        holder.setSliderOverride(serial, idx, color == null ? null : new SingleSliderLightingConfig().setMode(SINGLE_SLIDER_MODE.STATIC).setColor1(color));
        return true;
    }

    /**
     * Sends the lights to the panel and tells the UI. While a light pulses the panel gets every frame but the UI
     * at most every {@link #PULSE_UI_MS}; the frame that ends the pulse always reaches it.
     */
    private void relight(Device device, long now, boolean pulsing) {
        try {
            var lc = device.lightingConfig();
            if (lc != null) {
                device.setLighting(lc, true);
            }
        } catch (Exception e) {
            log.debug("Unable to re-send notification lights for {}", device.getSerialNumber(), e);
            return;
        }
        var serial = device.getSerialNumber();
        var last = uiNotifiedAt.get(serial);
        if (!pulsing || last == null || now - last >= PULSE_UI_MS) {
            uiNotifiedAt.put(serial, now);
            visualColorsChanged.fire(new VisualColorsChangedEvent(serial));
        }
    }
}
