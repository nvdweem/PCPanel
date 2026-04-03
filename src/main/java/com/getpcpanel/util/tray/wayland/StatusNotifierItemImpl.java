package com.getpcpanel.util.tray.wayland;

import static com.getpcpanel.util.tray.wayland.TrayServiceWayland.SNI_BUS_NAME;

import java.util.Objects;

import org.freedesktop.dbus.annotations.DBusInterfaceName;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;

import com.getpcpanel.ui.HomePage;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import lombok.RequiredArgsConstructor;
import lombok.extern.jbosslog.JBossLog;

@JBossLog
@RequiredArgsConstructor
@DBusInterfaceName(SNI_BUS_NAME)
public class StatusNotifierItemImpl implements StatusNotifierItem {
    @Inject
    Event<Object> eventBus;

    // @Override
    @Override
    public void Activate(int x, int y) {
        log.debug("Tray Activate (left-click) at {},{}", x, y);
        Platform.runLater(() ->
                eventBus.fire(new HomePage.ShowMainEvent())
        );
    }

    @Override
    public void ContextMenu(int x, int y) {
        log.debug("Tray ContextMenu (right-click) at {},{}", x, y);
        Platform.runLater(() -> {
            var alert = new Alert(
                    Alert.AlertType.CONFIRMATION,
                    "Exit PCPanel?",
                    ButtonType.YES,
                    ButtonType.NO
            );
            alert.setTitle("PCPanel");
            alert.setHeaderText(null);
            alert.showAndWait().ifPresent(response -> {
                if (Objects.equals(response, ButtonType.YES)) {
                    //noinspection CallToSystemExit
                    System.exit(0);
                }
            });
        });
    }

    @Override
    public void SecondaryActivate(int x, int y) {
        ContextMenu(x, y);
    }

    @Override
    public void Scroll(int delta, String orientation) {
    }
}
