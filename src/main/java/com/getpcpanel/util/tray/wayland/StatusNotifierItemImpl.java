package com.getpcpanel.util.tray.wayland;

import static com.getpcpanel.util.tray.wayland.TrayServiceWayland.SNI_BUS_NAME;

import java.util.Objects;

import org.freedesktop.dbus.annotations.DBusInterfaceName;
import org.springframework.context.ApplicationEventPublisher;

import com.getpcpanel.ui.HomePage;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Log4j2
@RequiredArgsConstructor
@DBusInterfaceName(SNI_BUS_NAME)
public class StatusNotifierItemImpl implements StatusNotifierItem {
    private final ApplicationEventPublisher eventPublisher;

    // @Override
    @Override
    public void Activate(int x, int y) {
        log.debug("Tray Activate (left-click) at {},{}", x, y);
        Platform.runLater(() ->
                eventPublisher.publishEvent(new HomePage.ShowMainEvent())
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
