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
        // Previously this quit the app on right-click — a footgun (the maintainer agreed it is not a
        // normal way to quit, #100). Quitting now lives in the web UI (Settings → Quit), so any tray
        // click just opens the UI. A proper Open/Quit dbusmenu is the remaining follow-up for #100.
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
