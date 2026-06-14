package com.getpcpanel.util.tray.wayland;

import java.nio.file.Files;
import java.nio.file.Path;

import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.exceptions.DBusExecutionException;
import org.freedesktop.dbus.interfaces.DBusInterface;

import com.getpcpanel.platform.LinuxBuild;
import com.getpcpanel.util.tray.ITrayService;

import jakarta.annotation.Nonnull;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import lombok.extern.log4j.Log4j2;

@Log4j2
@ApplicationScoped
@LinuxBuild
public class TrayServiceWayland implements ITrayService {
    static final String SNI_BUS_NAME = "org.kde.StatusNotifierItem";
    static final String WATCHER_BUS_NAME = "org.kde.StatusNotifierWatcher";
    static final String WATCHER_OBJECT_PATH = "/StatusNotifierWatcher";

    @Inject Event<Object> eventBus;
    private DBusConnection connection;

    @Override
    @PostConstruct
    public void init() {
        // Catch Throwable, not just Exception: in a GraalVM native image a missing/unreachable
        // class (e.g. com.sun.security.auth.module.UnixSystem, see #86) surfaces as a
        // NoClassDefFoundError/LinkageError. The tray is non-essential, so any failure here must
        // degrade to "no tray" rather than crash the whole app on startup.
        try {
            connection = DBusConnectionBuilder.forSessionBus()
                                              .transportConfig()
                                              .configureSasl()
                                              // Provide the uid explicitly so dbus-java never falls back to
                                              // com.sun.security.auth.module.UnixSystem for SASL EXTERNAL auth (#86).
                                              .withSaslUid(currentUid())
                                              .back()
                                              .back()
                                              .build();
            registerIcon();
            log.debug("Wayland tray initialized");
        } catch (DBusException e) {
            log.warn("D-Bus connection failed, running without a system tray icon: {}", e.getMessage());
        } catch (Throwable e) { // NOSONAR - intentionally broad; the tray must never take down the app
            log.warn("Could not initialize Wayland tray, running without a system tray icon: {}", e.toString());
            log.debug("Wayland tray initialization failure", e);
        }
    }

    private void registerIcon() throws DBusException {
        DBusInterface menuBarObject = () -> "/MenuBar";
        var statusNotifierItem = new StatusNotifierItemImpl();

        var wellKnownName = requestSniBus(1);
        connection.exportObject("/MenuBar", menuBarObject);
        connection.exportObject(statusNotifierItem);
        registerWithWatcher(wellKnownName);
    }

    private @Nonnull String requestSniBus(int id) {
        var wkn = SNI_BUS_NAME;
        try {
            wkn += "-" + ProcessHandle.current().pid() + "-" + id;
            connection.requestBusName(wkn);
            return wkn;
        } catch (DBusException e) {
            log.error("Could not request well-known bus name", e);
            return "error";
        }
    }

    private void registerWithWatcher(String wellKnownName) {
        // RegisterStatusNotifierItem throws a ServiceUnknown (a DBusExecutionException, i.e. an
        // unchecked RuntimeException) when no StatusNotifierWatcher is registered on the connected
        // bus - common inside Distrobox/Flatpak sandboxes or on desktops without an SNI host (#89).
        // Log it as a concise one-liner instead of a noisy multi-line stack trace.
        try {
            getStatusNotifierWatcher().RegisterStatusNotifierItem(wellKnownName);
        } catch (DBusException | DBusExecutionException e) {
            log.warn("No system tray available on this D-Bus session; running without a tray icon ({})", e.getMessage());
        }
    }

    private StatusNotifierWatcher getStatusNotifierWatcher() throws DBusException {
        return connection.getRemoteObject(
                WATCHER_BUS_NAME,
                WATCHER_OBJECT_PATH,
                StatusNotifierWatcher.class
        );
    }

    /**
     * Resolves the current user's uid without relying on {@code com.sun.security.auth.module.UnixSystem},
     * which is backed by a JNI library that is not reliably present in a GraalVM native image (#86).
     * The user's home directory is owned by their uid, so its {@code unix:uid} file attribute gives it.
     */
    private static long currentUid() {
        try {
            var home = System.getProperty("user.home");
            if (home != null) {
                var uid = Files.getAttribute(Path.of(home), "unix:uid");
                if (uid instanceof Integer i) {
                    return i.longValue();
                }
            }
        } catch (Exception e) { // NOSONAR - any failure falls back to 0 below
            log.debug("Could not determine uid via file attributes, falling back to 0", e);
        }
        return 0;
    }
}
