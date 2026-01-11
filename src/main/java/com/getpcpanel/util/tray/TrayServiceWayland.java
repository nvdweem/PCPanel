package com.getpcpanel.util.tray;

import java.util.Map;
import java.util.Objects;

import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.interfaces.Properties;
import org.freedesktop.dbus.types.Variant;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import com.getpcpanel.spring.ConditionalOnWayland;
import com.getpcpanel.ui.HomePage;

import jakarta.annotation.Nullable;
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
class TrayServiceWayland {
    private static final String WATCHER_BUS_NAME = "org.kde.StatusNotifierWatcher";
    private static final String WATCHER_OBJECT_PATH = "/StatusNotifierWatcher";
    private static final String ITEM_OBJECT_PATH = "/StatusNotifierItem";

    private final ApplicationEventPublisher eventPublisher;
    private DBusConnection connection;
    private String serviceName;

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
        } catch (Exception e) {
            log.warn("Wayland tray not available: {}", e.getMessage());
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

    /**
     * Inner class implementing the StatusNotifierItem interface.
     * Handles tray icon properties and click events.
     */
    private class PCPanelStatusNotifierItem implements StatusNotifierItem, Properties {

        private static final String SNI_INTERFACE = "org.kde.StatusNotifierItem";

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
            return "audio-volume-high";
        }

        @Override
        public String getTitle() {
            return "PCPanel";
        }

        @Override
        public Boolean getItemIsMenu() {
            return Boolean.FALSE;
        }

        // Properties interface implementation

        @Override
        public <A> @Nullable A Get(String interfaceName, String propertyName) {
            if (!SNI_INTERFACE.equals(interfaceName)) {
                return null;
            }
            return (A) switch (propertyName) {
                case "Category" -> getCategory();
                case "Id" -> getId();
                case "Status" -> getStatus();
                case "IconName" -> getIconName();
                case "Title" -> getTitle();
                case "ItemIsMenu" -> getItemIsMenu();
                default -> null;
            };
        }

        @Override
        public <A> void Set(String interfaceName, String propertyName, A value) {
            // Read-only properties
        }

        @Override
        public Map<String, Variant<?>> GetAll(String interfaceName) {
            if (!SNI_INTERFACE.equals(interfaceName)) {
                return Map.of();
            }
            return Map.of(
                    "Category", new Variant<>(getCategory()),
                    "Id", new Variant<>(getId()),
                    "Status", new Variant<>(getStatus()),
                    "IconName", new Variant<>(getIconName()),
                    "Title", new Variant<>(getTitle()),
                    "ItemIsMenu", new Variant<>(getItemIsMenu())
            );
        }

        @Override
        public void Activate(int x, int y) {
            log.info("Tray Activate (left-click) at {},{}", x, y);
            Platform.runLater(() ->
                eventPublisher.publishEvent(new HomePage.ShowMainEvent())
            );
        }

        @Override
        public void ContextMenu(int x, int y) {
            log.info("Tray ContextMenu (right-click) at {},{}", x, y);
            Platform.runLater(() -> {
                var alert = new javafx.scene.control.Alert(
                    javafx.scene.control.Alert.AlertType.CONFIRMATION,
                    "Exit PCPanel?",
                    javafx.scene.control.ButtonType.YES,
                    javafx.scene.control.ButtonType.NO
                );
                alert.setTitle("PCPanel");
                alert.setHeaderText(null);
                alert.showAndWait().ifPresent(response -> {
                    if (response == javafx.scene.control.ButtonType.YES) {
                        System.exit(0);
                    }
                });
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
