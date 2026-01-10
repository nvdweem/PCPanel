package com.getpcpanel.util.tray;

import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder;
import org.freedesktop.dbus.exceptions.DBusException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import com.getpcpanel.spring.ConditionalOnWayland;
import com.getpcpanel.ui.HomePage;
import com.getpcpanel.util.ITrayService;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import javafx.application.Platform;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

/**
 * Wayland system tray implementation using StatusNotifierItem (SNI) D-Bus protocol.
 * Works with Sway, KDE Plasma, GNOME (with extension), and other SNI-compatible desktops.
 */
@Log4j2
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(value = "disable.tray", havingValue = "false", matchIfMissing = true)
@ConditionalOnWayland
public class TrayServiceWayland implements ITrayService {
    private static final String WATCHER_BUS_NAME = "org.kde.StatusNotifierWatcher";
    private static final String WATCHER_OBJECT_PATH = "/StatusNotifierWatcher";
    private static final String ITEM_OBJECT_PATH = "/StatusNotifierItem";

    private final ApplicationEventPublisher eventPublisher;
    private DBusConnection connection;
    private boolean trayDisabled = false;
    private String serviceName;

    @Override
    @PostConstruct
    public void init() {
        try {
            connection = DBusConnectionBuilder.forSessionBus().build();

            // Service name format: org.kde.StatusNotifierItem-{PID}-{ID}
            serviceName = "org.kde.StatusNotifierItem-" + ProcessHandle.current().pid() + "-1";
            connection.requestBusName(serviceName);

            // Export our StatusNotifierItem implementation
            connection.exportObject(ITEM_OBJECT_PATH, new PCPanelStatusNotifierItem());

            // Register with the StatusNotifierWatcher
            var watcher = connection.getRemoteObject(
                    WATCHER_BUS_NAME,
                    WATCHER_OBJECT_PATH,
                    StatusNotifierWatcher.class
            );
            watcher.RegisterStatusNotifierItem(serviceName);

            log.info("Wayland tray icon registered via SNI protocol");
        } catch (DBusException e) {
            log.warn("D-Bus connection failed: {}", e.getMessage());
            trayDisabled = true;
        } catch (Exception e) {
            log.warn("Wayland tray not available: {}", e.getMessage());
            trayDisabled = true;
        }
    }

    @PreDestroy
    public void cleanup() {
        if (connection != null) {
            try {
                connection.releaseBusName(serviceName);
                connection.close();
            } catch (Exception e) {
                log.debug("Error closing D-Bus connection", e);
            }
        }
    }

    @Override
    public boolean isTrayDisabled() {
        return trayDisabled;
    }

    /**
     * Inner class implementing the StatusNotifierItem interface.
     * Handles tray icon properties and click events.
     */
    private class PCPanelStatusNotifierItem implements StatusNotifierItem {

        @Override
        public String getObjectPath() {
            return ITEM_OBJECT_PATH;
        }

        @Override
        public String getCategory() {
            return "ApplicationStatus";
        }

        @Override
        public String getId() {
            return "pcpanel";
        }

        @Override
        public String getStatus() {
            return "Active";
        }

        @Override
        public String getIconName() {
            // Use absolute path since PCPanel isn't installed to standard icon dirs
            // TODO: Consider using IconPixmap for embedded icon data
            return "audio-volume-high";
        }

        @Override
        public String getTitle() {
            return "PCPanel";
        }

        @Override
        public void Activate(int x, int y) {
            // Left-click: show main window
            Platform.runLater(() ->
                eventPublisher.publishEvent(new HomePage.ShowMainEvent())
            );
        }

        @Override
        public void ContextMenu(int x, int y) {
            // Right-click: for now, just exit
            // TODO: Show proper context menu with DBusMenu or JavaFX popup
            Platform.runLater(() -> {
                log.info("Tray context menu requested - exiting");
                System.exit(0);
            });
        }

        @Override
        public void SecondaryActivate(int x, int y) {
            // Middle-click: no action
        }

        @Override
        public void Scroll(int delta, String orientation) {
            // Scroll: no action
        }

        @Override
        public boolean isRemote() {
            return false;
        }
    }
}
