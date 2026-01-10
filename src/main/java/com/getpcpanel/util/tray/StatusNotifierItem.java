package com.getpcpanel.util.tray;

import org.freedesktop.dbus.annotations.DBusInterfaceName;
import org.freedesktop.dbus.annotations.DBusProperty;
import org.freedesktop.dbus.annotations.DBusProperty.Access;
import org.freedesktop.dbus.interfaces.DBusInterface;

/**
 * D-Bus interface for StatusNotifierItem (SNI) protocol.
 * Used by Wayland compositors like Sway for system tray icons.
 *
 * @see <a href="https://www.freedesktop.org/wiki/Specifications/StatusNotifierItem/">SNI Specification</a>
 */
@DBusInterfaceName("org.kde.StatusNotifierItem")
@DBusProperty(name = "Category", type = String.class, access = Access.READ)
@DBusProperty(name = "Id", type = String.class, access = Access.READ)
@DBusProperty(name = "Status", type = String.class, access = Access.READ)
@DBusProperty(name = "IconName", type = String.class, access = Access.READ)
@DBusProperty(name = "Title", type = String.class, access = Access.READ)
@DBusProperty(name = "ItemIsMenu", type = Boolean.class, access = Access.READ)
public interface StatusNotifierItem extends DBusInterface {

    // Properties (exposed via D-Bus Properties interface)
    String getCategory();
    String getId();
    String getStatus();
    String getIconName();
    String getTitle();
    Boolean getItemIsMenu();

    // Methods called by the StatusNotifierHost (tray implementation)

    /**
     * Called when user left-clicks the tray icon.
     * @param x screen x coordinate
     * @param y screen y coordinate
     */
    void Activate(int x, int y);

    /**
     * Called when user right-clicks the tray icon.
     * @param x screen x coordinate
     * @param y screen y coordinate
     */
    void ContextMenu(int x, int y);

    /**
     * Called when user middle-clicks the tray icon.
     * @param x screen x coordinate
     * @param y screen y coordinate
     */
    void SecondaryActivate(int x, int y);

    /**
     * Called when user scrolls over the tray icon.
     * @param delta scroll amount
     * @param orientation horizontal or vertical
     */
    void Scroll(int delta, String orientation);
}
