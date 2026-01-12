package com.getpcpanel.util.tray;

import org.freedesktop.dbus.annotations.DBusInterfaceName;
import org.freedesktop.dbus.interfaces.DBusInterface;

/**
 * D-Bus interface for StatusNotifierWatcher.
 * The watcher is provided by the desktop environment (e.g., swaybar).
 * Applications register their StatusNotifierItem with the watcher.
 *
 * @see <a href="https://www.freedesktop.org/wiki/Specifications/StatusNotifierItem/StatusNotifierWatcher/">Watcher Specification</a>
 */
@DBusInterfaceName("org.kde.StatusNotifierWatcher")
public interface StatusNotifierWatcher extends DBusInterface {

    /**
     * Register a StatusNotifierItem with the watcher.
     * @param service the D-Bus service name of the StatusNotifierItem
     */
    void RegisterStatusNotifierItem(String service);
}
