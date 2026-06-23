package com.getpcpanel.overlay;

import java.awt.Image;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder;
import org.freedesktop.dbus.interfaces.DBus;
import org.freedesktop.dbus.types.UInt32;
import org.freedesktop.dbus.types.Variant;

import com.getpcpanel.profile.Save;

import lombok.extern.log4j.Log4j2;

/**
 * Linux/Wayland volume overlay. Where Windows draws a JNA layered window ({@link Win32VolumeOverlay}),
 * Wayland has no client-controlled window placement, so the overlay is drawn by the desktop itself
 * over D-Bus — AWT-free and so safe in the native image (which bundles no libawt on Linux):
 *
 * <ul>
 *   <li><b>KDE Plasma</b> (primary): the native {@link KdeOsdService} volume OSD — the same bar Plasma
 *       shows for its own volume keys. Repeated calls update one OSD in place, in real time, and it
 *       auto-hides. This is the correct, real-time overlay.</li>
 *   <li><b>Other desktops</b> (fallback): a {@link FreedesktopNotifications} notification with a value
 *       bar. This is a degraded experience (it's a notification, not a real-time OSD) but is the only
 *       compositor-agnostic option without a custom Wayland surface; it is used only when the KDE OSD
 *       service isn't on the bus.</li>
 * </ul>
 *
 * <p>All work happens on a dedicated daemon thread: {@link #show} is called synchronously on the HID
 * input thread and must never block it. Failures are tolerated (a transient D-Bus error must not kill
 * the overlay — that was the old "shows once then never again" bug); the overlay only gives up after a
 * run of consecutive failures.
 *
 * <p>Position/colour/size settings ({@link Save}) are <em>not</em> honoured: the desktop owns the OSD's
 * appearance and placement. The settings UI greys those out on Linux.
 */
@Log4j2
public class LinuxOverlay implements OverlayWindow {
    private static final String PLASMA_BUS = "org.kde.plasmashell";
    private static final String OSD_PATH = "/org/kde/osdService";
    private static final String NOTIF_BUS = "org.freedesktop.Notifications";
    private static final String NOTIF_PATH = "/org/freedesktop/Notifications";
    private static final String APP_NAME = "PCPanel";
    private static final String SYNC_TAG = "pcpanel-volume";
    private static final int EXPIRE_MS = 1500;
    /** Give up (and stop logging) only after this many consecutive failures, not a single hiccup. */
    private static final int MAX_CONSECUTIVE_FAILURES = 10;

    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        var t = new Thread(r, "PCPanel Overlay");
        t.setDaemon(true);
        return t;
    });
    private final AtomicBoolean scheduled = new AtomicBoolean();

    private volatile float pendingValue;
    private volatile boolean showNumber;

    // The fields below are only ever touched on the worker thread.
    private DBusConnection connection;
    private KdeOsdService kdeOsd;                 // non-null once connected on KDE
    private FreedesktopNotifications notifications; // non-null once connected elsewhere
    private UInt32 lastNotifId = new UInt32(0);
    private int consecutiveFailures;
    private boolean disabled; // tripped only after MAX_CONSECUTIVE_FAILURES

    @Override
    public void show(float value, Image icon) {
        pendingValue = value;
        // Coalesce bursts: while a send is queued/in-flight, just update pendingValue. One more flush
        // is scheduled so the latest value is always delivered, without a task per knob tick. Because
        // each flush sends the newest value, the OSD tracks the knob in real time.
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
            ensureConnected();
            var percent = value < 0 ? -1 : Math.max(0, Math.min(100, Math.round(value * 100f)));
            if (kdeOsd != null) {
                if (percent >= 0) {
                    kdeOsd.volumeChanged(percent);
                } else {
                    kdeOsd.showText("audio-card", APP_NAME);
                }
            } else {
                sendNotification(percent);
            }
            consecutiveFailures = 0;
        } catch (Throwable t) { // NOSONAR - the overlay is cosmetic and must never take down the app
            // Tolerate transient errors: only a sustained run of failures disables the overlay, so a
            // single D-Bus hiccup can't silently kill it for the rest of the session.
            if (++consecutiveFailures == 1) {
                log.warn("Overlay D-Bus call failed (will keep trying): {}", t.toString());
            }
            log.debug("Overlay D-Bus failure #{}", consecutiveFailures, t);
            if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                disabled = true;
                log.warn("Disabling the Linux overlay after {} consecutive failures", consecutiveFailures);
            }
        }
    }

    private void ensureConnected() throws Exception { // NOSONAR - DBus throws broadly; flush() handles it
        if (connection != null) {
            return;
        }
        var conn = DBusConnectionBuilder.forSessionBus()
                                        .transportConfig()
                                        .configureSasl()
                                        // Supply the uid explicitly so dbus-java never falls back to
                                        // com.sun.security.auth.module.UnixSystem (unreliable in the native image, #86).
                                        .withSaslUid(currentUid())
                                        .back()
                                        .back()
                                        .build();
        // Prefer KDE's native volume OSD when Plasma is on the bus; otherwise degrade to notifications.
        if (hasOwner(conn, PLASMA_BUS)) {
            kdeOsd = conn.getRemoteObject(PLASMA_BUS, OSD_PATH, KdeOsdService.class);
            log.info("Linux overlay: using the native KDE Plasma volume OSD");
        } else {
            notifications = conn.getRemoteObject(NOTIF_BUS, NOTIF_PATH, FreedesktopNotifications.class);
            log.info("Linux overlay: KDE OSD not available, falling back to desktop notifications");
        }
        connection = conn;
    }

    private static boolean hasOwner(DBusConnection conn, String busName) {
        try {
            var dbus = conn.getRemoteObject("org.freedesktop.DBus", "/org/freedesktop/DBus", DBus.class);
            return dbus.NameHasOwner(busName);
        } catch (Exception e) { // NOSONAR - treat any probe error as "not available"
            return false;
        }
    }

    private void sendNotification(int percent) {
        var summary = percent < 0 ? APP_NAME : (showNumber ? percent + "%" : "Volume");
        var hints = new HashMap<String, Variant<?>>();
        hints.put("urgency", new Variant<>((byte) 0)); // low
        hints.put("suppress-sound", new Variant<>(Boolean.TRUE));
        hints.put("transient", new Variant<>(Boolean.TRUE));
        hints.put("x-canonical-private-synchronous", new Variant<>(SYNC_TAG));
        if (percent >= 0) {
            hints.put("value", new Variant<>(percent));
        }
        lastNotifId = notifications.Notify(APP_NAME, lastNotifId, iconFor(percent), summary, "", List.of(), hints, EXPIRE_MS);
    }

    /** Standard freedesktop icon names so the fallback OSD looks native; {@code percent < 0} is a button action. */
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

    // The desktop owns the OSD's placement and size on Wayland, so there is nothing to position or
    // measure here. Reporting a zero size makes Overlay.determinePosition() a no-op.
    @Override
    public void setLocation(int x, int y) {
        // managed by the desktop
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
