package com.getpcpanel.overlay;

import java.awt.Image;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder;
import org.freedesktop.dbus.types.UInt32;
import org.freedesktop.dbus.types.Variant;

import com.getpcpanel.profile.Save;

import lombok.extern.log4j.Log4j2;

/**
 * Linux/Wayland volume overlay. Where Windows draws a JNA layered window ({@link Win32VolumeOverlay}),
 * Wayland has no client-controlled window placement, so the overlay is rendered as an on-screen
 * notification through the freedesktop {@link FreedesktopNotifications} D-Bus service. This is
 * compositor-agnostic (KDE Plasma, GNOME, sway/mako, …) and AWT-free, so it is safe in the native
 * image (which bundles no libawt on Linux).
 *
 * <p>The {@code "value"} hint makes notification daemons render a volume-style progress bar, and the
 * notification id is reused (plus the {@code x-canonical-private-synchronous} hint) so rapid knob
 * turns update a single OSD in place rather than stacking. All work happens on a dedicated daemon
 * thread: {@link #show} is called synchronously on the HID input thread and must never block it.
 *
 * <p>Position/colour/size settings ({@link Save}) are <em>not</em> honoured here — on Wayland the
 * notification daemon owns the OSD's appearance and placement. Only the on/off master switch and the
 * value itself carry over.
 */
@Log4j2
public class LinuxNotifyOverlay implements OverlayWindow {
    private static final String BUS_NAME = "org.freedesktop.Notifications";
    private static final String OBJECT_PATH = "/org/freedesktop/Notifications";
    private static final String APP_NAME = "PCPanel";
    /** Groups our notifications into one replaceable OSD slot on daemons that honour the hint (KDE/GNOME). */
    private static final String SYNC_TAG = "pcpanel-volume";
    private static final int EXPIRE_MS = 1500;

    private final java.util.concurrent.ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        var t = new Thread(r, "PCPanel Overlay");
        t.setDaemon(true);
        return t;
    });
    private final AtomicBoolean scheduled = new AtomicBoolean();

    private volatile float pendingValue;
    private volatile boolean showNumber;

    // The fields below are only ever touched on the worker thread.
    private DBusConnection connection;
    private FreedesktopNotifications notifications;
    private UInt32 lastId = new UInt32(0);
    private boolean disabled; // a previous init/send failure; stop trying so we don't spam the log

    @Override
    public void show(float value, Image icon) {
        pendingValue = value;
        // Coalesce bursts: while a send is queued/in-flight, just update pendingValue. One more flush
        // is scheduled so the latest value is always delivered, without queueing a task per knob tick.
        if (scheduled.compareAndSet(false, true)) {
            worker.execute(this::flush);
        }
    }

    private void flush() {
        scheduled.set(false);
        if (disabled) {
            return;
        }
        var value = pendingValue;
        try {
            if (!ensureConnected()) {
                return;
            }
            var percent = value < 0 ? -1 : Math.max(0, Math.min(100, Math.round(value * 100f)));
            var summary = percent < 0 ? APP_NAME : (showNumber ? percent + "%" : "Volume");
            var hints = new HashMap<String, Variant<?>>();
            hints.put("urgency", new Variant<>((byte) 0)); // low
            hints.put("suppress-sound", new Variant<>(Boolean.TRUE));
            hints.put("transient", new Variant<>(Boolean.TRUE)); // OSD, don't keep in history
            hints.put("x-canonical-private-synchronous", new Variant<>(SYNC_TAG));
            if (percent >= 0) {
                hints.put("value", new Variant<>(percent));
            }
            lastId = notifications.Notify(APP_NAME, lastId, iconFor(percent), summary, "", List.of(), hints, EXPIRE_MS);
        } catch (Throwable t) { // NOSONAR - the overlay is cosmetic and must never take down the app
            // Disable after the first failure: a broken/absent notification service won't fix itself
            // mid-session, and this runs on every knob turn.
            disabled = true;
            log.warn("Notification overlay unavailable, disabling it for this session: {}", t.toString());
            log.debug("Notification overlay failure", t);
        }
    }

    private boolean ensureConnected() throws Exception { // NOSONAR - DBus throws broadly; caller handles it
        if (notifications != null) {
            return true;
        }
        connection = DBusConnectionBuilder.forSessionBus()
                                          .transportConfig()
                                          .configureSasl()
                                          // Supply the uid explicitly so dbus-java never falls back to
                                          // com.sun.security.auth.module.UnixSystem (unreliable in the native image, #86).
                                          .withSaslUid(currentUid())
                                          .back()
                                          .back()
                                          .build();
        notifications = connection.getRemoteObject(BUS_NAME, OBJECT_PATH, FreedesktopNotifications.class);
        return true;
    }

    /** Standard freedesktop icon names so the OSD looks native; {@code percent < 0} is a non-volume (button) action. */
    private static String iconFor(int percent) {
        if (percent < 0) {
            return "audio-card";
        }
        if (percent == 0) {
            return "audio-volume-muted";
        }
        if (percent < 34) {
            return "audio-volume-low";
        }
        if (percent < 67) {
            return "audio-volume-medium";
        }
        return "audio-volume-high";
    }

    @Override
    public void setStyles(Save save) {
        if (save != null) {
            showNumber = save.isOverlayShowNumber();
        }
    }

    // The notification daemon owns the OSD's placement and size on Wayland, so there is nothing to
    // position or measure here. Reporting a zero size makes Overlay.determinePosition() a no-op.
    @Override
    public void setLocation(int x, int y) {
        // managed by the notification daemon
    }

    @Override
    public int getWidth() {
        return 0;
    }

    @Override
    public int getHeight() {
        return 0;
    }

    @Override
    public ScreenSize getScreenSize() {
        return new ScreenSize(0, 0);
    }

    /**
     * Resolves the current user's uid without {@code com.sun.security.auth.module.UnixSystem}, whose
     * JNI backing is not reliably present in a GraalVM native image (#86). The home directory is owned
     * by the user's uid, so its {@code unix:uid} attribute gives it.
     */
    private static long currentUid() {
        try {
            var home = System.getProperty("user.home");
            if (home != null && Files.getAttribute(Path.of(home), "unix:uid") instanceof Integer uid) {
                return uid.longValue();
            }
        } catch (Exception e) { // NOSONAR - any failure falls back to 0 below
            log.debug("Could not determine uid via file attributes, falling back to 0", e);
        }
        return 0;
    }
}
