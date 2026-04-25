package com.getpcpanel.util.tray.wayland;

import static com.getpcpanel.util.tray.wayland.TrayServiceWayland.SNI_BUS_NAME;

import com.getpcpanel.util.ShowMainEvent;

import org.freedesktop.dbus.annotations.DBusInterfaceName;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import lombok.extern.log4j.Log4j2;

@Log4j2
@DBusInterfaceName(SNI_BUS_NAME)
public class StatusNotifierItemImpl implements StatusNotifierItem {
    @Inject
    Event<Object> eventBus;

    @Override
    public void Activate(int x, int y) {
        log.debug("Tray Activate (left-click) at {},{}", x, y);
        eventBus.fire(new ShowMainEvent());
    }

    @Override
    public void ContextMenu(int x, int y) {
        log.debug("Tray ContextMenu (right-click) at {},{}", x, y);
        // Headless Quarkus app — exit on right-click confirmed by user via the web UI
        System.exit(0);
    }

    @Override
    public void SecondaryActivate(int x, int y) {
        ContextMenu(x, y);
    }

    @Override
    public void Scroll(int delta, String orientation) {
    }
}
