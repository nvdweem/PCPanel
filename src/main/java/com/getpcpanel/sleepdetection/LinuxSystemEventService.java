package com.getpcpanel.sleepdetection;

import java.nio.file.Files;
import java.nio.file.Path;

import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;
import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder;
import org.freedesktop.dbus.interfaces.Properties;

import com.getpcpanel.platform.LinuxBuild;

import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import lombok.extern.log4j.Log4j2;

/**
 * Linux sleep/session detection via systemd-logind on the system D-Bus. Truly native and event-driven
 * (no JNA callbacks, which crash the native image): the {@code PrepareForSleep} signal gives advance
 * suspend notice and a resume signal. Locking is followed through the app's session: its {@code LockedHint}
 * property (set by the desktop's own screen locker, as on KDE Plasma and GNOME) and its {@code Lock}/{@code Unlock}
 * signals (logind asked to lock, e.g. {@code loginctl lock-session}). dbus-java is the same stack the system tray already uses, so it works
 * in the GraalVM native image.
 *
 * <p>Display power off/on (monitors sleeping) is detected separately by {@link LinuxDisplayPowerMonitor}
 * polling X11 DPMS, since logind has no monitor-power signal.
 */
@Log4j2
@Startup
@ApplicationScoped
@LinuxBuild
public class LinuxSystemEventService {
    private static final String LOGIN1 = "org.freedesktop.login1";
    private static final String SESSION_INTERFACE = "org.freedesktop.login1.Session";
    private static final String DEFAULT_SYSTEM_BUS = "/var/run/dbus/system_bus_socket";
    private static final String NO_LOGIND = "Locking the PC and sleep can't be detected: systemd-logind isn't reachable (a distribution without systemd, or a sandbox without access to it).";

    @Inject
    Event<Object> eventBus;

    private volatile DBusConnection connection;
    private volatile boolean closed;
    /** Why locking or sleep can't be followed, for the settings page; {@code null} when they can. */
    @Nullable private volatile String lockAndSleepUnavailable;
    private final LinuxDisplayPowerMonitor displayPowerMonitor = new LinuxDisplayPowerMonitor(this::fire);

    @PostConstruct
    public void init() {
        displayPowerMonitor.start();
        // Connecting waits out dbus-java's retries when there is no system bus (a Flatpak without a system-bus
        // grant took ~9 s), so it must not hold up startup.
        var thread = new Thread(this::connect, "logind-connect");
        thread.setDaemon(true);
        thread.start();
    }

    private void connect() {
        if (!systemBusPresent(System.getenv("DBUS_SYSTEM_BUS_ADDRESS"))) {
            // dbus-java waits out its retries on a missing socket (~9 s), and every other D-Bus connection, such as the
            // tray's, waits with it: don't try.
            log.warn("No system D-Bus socket; running without systemd-logind sleep detection");
            lockAndSleepUnavailable = NO_LOGIND;
            return;
        }
        // Catch Throwable, not just Exception: in a native image a missing/unreachable class surfaces
        // as a LinkageError, and sleep detection is non-essential — it must never crash startup.
        try {
            var built = DBusConnectionBuilder.forSystemBus()
                                              .transportConfig()
                                              .configureSasl()
                                              // Resolve the uid ourselves so dbus-java never falls back to
                                              // com.sun.security.auth.module.UnixSystem for SASL EXTERNAL (see #86).
                                              .withSaslUid(currentUid())
                                              .back()
                                              .back()
                                              .build();

            built.addSigHandler(Login1Manager.PrepareForSleep.class, signal ->
                    fire(signal.start ? SystemEventType.goingToSuspend : SystemEventType.resumedFromSuspend));
            built.addSigHandler(Login1Session.Lock.class, signal -> fire(SystemEventType.locked));
            built.addSigHandler(Login1Session.Unlock.class, signal -> fire(SystemEventType.unlocked));
            // Lock/Unlock are logind asking the desktop to lock (loginctl lock-session). A desktop that locks on its
            // own, as KDE Plasma and GNOME do for their lock shortcut and idle lock, only sets the session's
            // LockedHint, so follow that too.
            var session = sessionPath(built);
            if (session != null) {
                built.addSigHandler(Properties.PropertiesChanged.class, signal -> onSessionChanged(session, signal));
            } else {
                lockAndSleepUnavailable = "Locking the PC with your desktop's own lock can't be detected (systemd-logind doesn't know this session); sleep still switches the lights off.";
            }
            connection = built;
            if (closed) {
                // The app shut down while this was still connecting.
                closeConnection();
                return;
            }

            log.info("Linux sleep/session detection started (systemd-logind)");
        } catch (Throwable e) { // NOSONAR - intentionally broad; sleep detection must never take down the app
            log.warn("Could not initialize systemd-logind sleep detection, running without it: {}", e.toString());
            lockAndSleepUnavailable = NO_LOGIND;
            log.debug("logind sleep detection initialization failure", e);
        }
    }

    /**
     * Whether the system bus's socket is there: the one {@code DBUS_SYSTEM_BUS_ADDRESS} names, or else the standard one.
     * An address that is not a plain socket path (abstract, tcp) is taken to be there.
     */
    static boolean systemBusPresent(@Nullable String address) {
        if (StringUtils.isBlank(address)) {
            return Files.exists(Path.of(DEFAULT_SYSTEM_BUS));
        }
        for (var part : StringUtils.split(StringUtils.substringBefore(address, ';'), ',')) {
            var path = StringUtils.substringAfter(part, "unix:path=");
            if (path.isEmpty() && part.startsWith("path=")) {
                path = part.substring("path=".length());
            }
            if (!path.isEmpty()) {
                return Files.exists(Path.of(path));
            }
        }
        return true;
    }

    /** Why locking the PC or sleep can't be followed here, or {@code null} when they can. */
    @Nullable
    public String lockAndSleepUnavailable() {
        return lockAndSleepUnavailable;
    }

    /** Why the screens turning off can't be followed here, or {@code null} when they can. */
    @Nullable
    public String screensOffUnavailable() {
        return displayPowerMonitor.unavailable();
    }

    /** This app's logind session: the one the desktop says it runs in, or else the one logind picks for it. */
    @Nullable
    private static String sessionPath(DBusConnection connection) {
        try {
            var manager = connection.getRemoteObject(LOGIN1, "/org/freedesktop/login1", Login1Manager.class);
            for (var id : new String[] { System.getenv("XDG_SESSION_ID"), "auto" }) {
                if (StringUtils.isBlank(id)) {
                    continue;
                }
                try {
                    return manager.GetSession(id).getPath();
                } catch (RuntimeException e) {
                    log.debug("logind has no session '{}': {}", id, e.toString());
                }
            }
        } catch (Exception e) {
            log.debug("Unable to look up the logind session", e);
        }
        log.info("No logind session found; locking is only noticed when logind is asked to lock");
        return null;
    }

    static @Nullable SystemEventType lockedHintChange(String session, Properties.PropertiesChanged signal) {
        if (!session.equals(signal.getPath()) || !SESSION_INTERFACE.equals(signal.getInterfaceName())) {
            return null;
        }
        var hint = signal.getPropertiesChanged().get("LockedHint");
        if (hint != null && hint.getValue() instanceof Boolean locked) {
            return locked ? SystemEventType.locked : SystemEventType.unlocked;
        }
        return null;
    }

    private void onSessionChanged(String session, Properties.PropertiesChanged signal) {
        var type = lockedHintChange(session, signal);
        if (type != null) {
            fire(type);
        }
    }

    @PreDestroy
    public void shutdown() {
        closed = true;
        displayPowerMonitor.stop();
        closeConnection();
    }

    private void closeConnection() {
        var connection = this.connection;
        if (connection != null) {
            try {
                connection.close();
            } catch (Exception e) { // NOSONAR - best-effort cleanup
                log.debug("Error closing logind D-Bus connection", e);
            }
        }
    }

    private void fire(SystemEventType type) {
        log.debug("Linux system event: {}", type);
        eventBus.fire(new SystemEvent(type));
    }

    /**
     * Resolve the current user's uid without {@code com.sun.security.auth.module.UnixSystem}, whose JNI
     * library is not reliably present in a native image (#86): the home directory is owned by the uid.
     */
    private static long currentUid() {
        try {
            var home = System.getProperty("user.home");
            if (home != null && Files.getAttribute(Path.of(home), "unix:uid") instanceof Integer uid) {
                return uid.longValue();
            }
        } catch (Exception e) { // NOSONAR - any failure falls back to 0
            log.debug("Could not determine uid via file attributes, falling back to 0", e);
        }
        return 0;
    }
}
