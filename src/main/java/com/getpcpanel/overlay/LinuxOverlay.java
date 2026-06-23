package com.getpcpanel.overlay;

import java.awt.Image;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder;
import org.freedesktop.dbus.interfaces.DBus;

import com.getpcpanel.profile.Save;

import lombok.extern.log4j.Log4j2;

/**
 * Linux/Wayland volume overlay. Where Windows draws a JNA layered window ({@link Win32VolumeOverlay}),
 * Wayland has no client-controlled window placement, so the overlay is drawn by the desktop itself
 * over D-Bus — AWT-free and so safe in the native image (which bundles no libawt on Linux).
 *
 * <p>This uses <b>KDE Plasma's</b> native volume OSD ({@link KdeOsdService#volumeChanged} on
 * {@code org.kde.plasmashell}) — the same bar Plasma shows for its own volume keys. Repeated calls
 * update one OSD in place, in real time, and it auto-hides. It is the only Linux desktop with a
 * verified, real-time OSD D-Bus API.
 *
 * <p>On any other desktop there is <b>no overlay</b> (a clean no-op after a one-time log). A
 * notification-based fallback was deliberately rejected: the freedesktop notification protocol can't
 * guarantee in-place replacement across daemons, so it risks spamming one notification per knob tick
 * — worse than no overlay. A real non-KDE overlay would need a custom {@code wlr-layer-shell} surface
 * (unsupported on GNOME/Mutter), which is out of scope here.
 *
 * <p>All work happens on a dedicated daemon thread: {@link #show} is called synchronously on the HID
 * input thread and must never block it. A transient D-Bus error doesn't kill the overlay (that was the
 * old "shows once then never again" bug) — it only gives up after a run of consecutive failures.
 *
 * <p>Position/colour/size settings ({@link Save}) are <em>not</em> honoured: the desktop owns the
 * OSD's appearance and placement. The settings UI greys those out on Linux.
 */
@Log4j2
public class LinuxOverlay implements OverlayWindow {
    private static final String PLASMA_BUS = "org.kde.plasmashell";
    private static final String OSD_PATH = "/org/kde/osdService";
    /** Give up (and stop logging) only after this many consecutive failures, not a single hiccup. */
    private static final int MAX_CONSECUTIVE_FAILURES = 10;

    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        var t = new Thread(r, "PCPanel Overlay");
        t.setDaemon(true);
        return t;
    });
    private final AtomicBoolean scheduled = new AtomicBoolean();

    private volatile float pendingValue;

    // The fields below are only ever touched on the worker thread.
    private DBusConnection connection;
    private KdeOsdService kdeOsd;       // non-null once connected on KDE
    private int consecutiveFailures;
    private boolean disabled;           // no supported desktop, or too many consecutive failures

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
            if (!ensureKdeOsd()) {
                return;
            }
            var percent = value < 0 ? -1 : Math.max(0, Math.min(100, Math.round(value * 100f)));
            if (percent >= 0) {
                kdeOsd.volumeChanged(percent);
            } else {
                kdeOsd.showText("audio-card", "PCPanel"); // non-volume (button) overlay
            }
            consecutiveFailures = 0;
        } catch (Throwable t) { // NOSONAR - the overlay is cosmetic and must never take down the app
            // Tolerate transient errors: only a sustained run of failures disables the overlay, so a
            // single D-Bus hiccup can't silently kill it for the rest of the session.
            if (++consecutiveFailures == 1) {
                log.warn("KDE OSD call failed (will keep trying): {}", t.toString());
            }
            log.debug("KDE OSD failure #{}", consecutiveFailures, t);
            if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                disabled = true;
                log.warn("Disabling the Linux overlay after {} consecutive failures", consecutiveFailures);
            }
        }
    }

    /**
     * Lazily connects and resolves the KDE OSD service. If Plasma isn't on the session bus, disables
     * the overlay for this session (no supported desktop) — a desktop won't grow {@code plasmashell}
     * mid-run, so retrying would be pointless.
     */
    private boolean ensureKdeOsd() throws Exception { // NOSONAR - DBus throws broadly; flush() handles it
        if (kdeOsd != null) {
            return true;
        }
        if (connection == null) {
            connection = DBusConnectionBuilder.forSessionBus()
                                              .transportConfig()
                                              .configureSasl()
                                              // Supply the uid explicitly so dbus-java never falls back to
                                              // com.sun.security.auth.module.UnixSystem (unreliable in the native image, #86).
                                              .withSaslUid(currentUid())
                                              .back()
                                              .back()
                                              .build();
        }
        if (!hasOwner(connection, PLASMA_BUS)) {
            disabled = true;
            log.info("Volume overlay disabled: no supported desktop OSD found on this session "
                    + "(only KDE Plasma's native OSD is supported on Linux).");
            return false;
        }
        kdeOsd = connection.getRemoteObject(PLASMA_BUS, OSD_PATH, KdeOsdService.class);
        log.info("Volume overlay: using the native KDE Plasma volume OSD");
        return true;
    }

    private static boolean hasOwner(DBusConnection conn, String busName) {
        try {
            var dbus = conn.getRemoteObject("org.freedesktop.DBus", "/org/freedesktop/DBus", DBus.class);
            return dbus.NameHasOwner(busName);
        } catch (Exception e) { // NOSONAR - treat any probe error as "not available"
            return false;
        }
    }

    // The desktop owns the OSD's appearance and placement, so styling settings don't apply here.
    @Override
    public void setStyles(Save save) {
        // nothing to apply — KDE renders the OSD
    }

    // Nothing to position or measure here. Reporting a zero size makes Overlay.determinePosition() a no-op.
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
