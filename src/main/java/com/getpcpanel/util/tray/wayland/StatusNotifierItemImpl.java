package com.getpcpanel.util.tray.wayland;

import static com.getpcpanel.util.tray.wayland.TrayServiceWayland.SNI_BUS_NAME;

import com.getpcpanel.util.AppEvents;
import com.getpcpanel.util.ShowMainEvent;

import org.freedesktop.dbus.annotations.DBusInterfaceName;
import lombok.extern.log4j.Log4j2;

@Log4j2
@DBusInterfaceName(SNI_BUS_NAME)
public class StatusNotifierItemImpl implements StatusNotifierItem {
    @Override
    public void Activate(int x, int y) {
        log.debug("Tray Activate (left-click) at {},{}", x, y);
        AppEvents.fire(new ShowMainEvent());
    }

    @Override
    public void ContextMenu(int x, int y) {
        // Any tray click opens the UI; quitting lives in the web UI (Settings → Quit). A proper
        // Open/Quit dbusmenu is a follow-up (#100).
        log.debug("Tray ContextMenu (right-click) at {},{}", x, y);
        AppEvents.fire(new ShowMainEvent());
    }

    @Override
    public void SecondaryActivate(int x, int y) {
        log.debug("Tray SecondaryActivate (middle-click) at {},{}", x, y);
        AppEvents.fire(new ShowMainEvent());
    }

    @Override
    public void Scroll(int delta, String orientation) {
    }
}
