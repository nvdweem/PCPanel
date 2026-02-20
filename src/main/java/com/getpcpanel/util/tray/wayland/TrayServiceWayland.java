package com.getpcpanel.util.tray.wayland;

import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.interfaces.DBusInterface;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import com.getpcpanel.spring.ConditionalOnWayland;

import jakarta.annotation.Nonnull;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(value = "disable.tray", havingValue = "false", matchIfMissing = true)
@ConditionalOnWayland
class TrayServiceWayland {
    static final String SNI_BUS_NAME = "org.kde.StatusNotifierItem";
    static final String WATCHER_BUS_NAME = "org.kde.StatusNotifierWatcher";
    static final String WATCHER_OBJECT_PATH = "/StatusNotifierWatcher";

    private final ApplicationEventPublisher eventPublisher;
    private DBusConnection connection;

    @PostConstruct
    public void init() {
        try {
            connection = DBusConnectionBuilder.forSessionBus().build();
            registerIcon();
            log.debug("Wayland tray initialized");
        } catch (DBusException e) {
            log.warn("D-Bus connection failed: {}", e.getMessage());
        } catch (Exception e) {
            log.error("Failed to initialize Wayland tray", e);
        }
    }

    private void registerIcon() throws DBusException {
        DBusInterface menuBarObject = () -> "/MenuBar";
        var statusNotifierItem = new StatusNotifierItemImpl(eventPublisher);

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
        try {
            getStatusNotifierWatcher().RegisterStatusNotifierItem(wellKnownName);
        } catch (DBusException e) {
            log.warn("StatusNotifierWatcher not available (Wayland tray may not work): {}", e.getMessage());
        }
    }

    private StatusNotifierWatcher getStatusNotifierWatcher() throws DBusException {
        return connection.getRemoteObject(
                WATCHER_BUS_NAME,
                WATCHER_OBJECT_PATH,
                StatusNotifierWatcher.class
        );
    }
}
