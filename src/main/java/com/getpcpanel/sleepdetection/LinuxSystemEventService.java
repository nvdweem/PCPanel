package com.getpcpanel.sleepdetection;

import java.nio.file.Files;
import java.nio.file.Path;

import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder;

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
 * suspend notice and a resume signal, and the per-session {@code Lock}/{@code Unlock} signals give
 * best-effort lock detection. dbus-java is the same stack the system tray already uses, so it works
 * in the GraalVM native image.
 */
@Log4j2
@Startup
@ApplicationScoped
@LinuxBuild
public class LinuxSystemEventService {
    @Inject
    Event<Object> eventBus;

    private DBusConnection connection;

    @PostConstruct
    public void init() {
        // Catch Throwable, not just Exception: in a native image a missing/unreachable class surfaces
        // as a LinkageError, and sleep detection is non-essential — it must never crash startup.
        try {
            connection = DBusConnectionBuilder.forSystemBus()
                                              .transportConfig()
                                              .configureSasl()
                                              // Resolve the uid ourselves so dbus-java never falls back to
                                              // com.sun.security.auth.module.UnixSystem for SASL EXTERNAL (see #86).
                                              .withSaslUid(currentUid())
                                              .back()
                                              .back()
                                              .build();

            connection.addSigHandler(Login1Manager.PrepareForSleep.class, signal ->
                    fire(signal.start ? SystemEventType.goingToSuspend : SystemEventType.resumedFromSuspend));
            connection.addSigHandler(Login1Session.Lock.class, signal -> fire(SystemEventType.locked));
            connection.addSigHandler(Login1Session.Unlock.class, signal -> fire(SystemEventType.unlocked));

            log.info("Linux sleep/session detection started (systemd-logind)");
        } catch (Throwable e) { // NOSONAR - intentionally broad; sleep detection must never take down the app
            log.warn("Could not initialize systemd-logind sleep detection, running without it: {}", e.toString());
            log.debug("logind sleep detection initialization failure", e);
        }
    }

    @PreDestroy
    public void shutdown() {
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
