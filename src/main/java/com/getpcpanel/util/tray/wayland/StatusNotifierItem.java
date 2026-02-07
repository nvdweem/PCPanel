package com.getpcpanel.util.tray.wayland;

import static com.getpcpanel.util.tray.wayland.TrayServiceWayland.SNI_BUS_NAME;

import org.freedesktop.dbus.DBusPath;
import org.freedesktop.dbus.annotations.DBusBoundProperty;
import org.freedesktop.dbus.annotations.DBusInterfaceName;
import org.freedesktop.dbus.annotations.DBusProperty;
import org.freedesktop.dbus.interfaces.DBusInterface;

/**
 * https://www.freedesktop.org/wiki/Specifications/StatusNotifierItem/
 */
@DBusInterfaceName(SNI_BUS_NAME)
public interface StatusNotifierItem extends DBusInterface {
    @Override
    default String getObjectPath() {
        return "/StatusNotifierItem";
    }

    @DBusBoundProperty(access = DBusProperty.Access.READ)
    default String getId() {
        return "PCPanel";
    }

    @DBusBoundProperty(access = DBusProperty.Access.READ)
    default String getStatus() {
        return "Active";
    }

    @DBusBoundProperty(access = DBusProperty.Access.READ)
    default DBusPath getMenu() {
        return DBusPath.of("MenuBar");
    }

    @DBusBoundProperty(access = DBusProperty.Access.READ)
    default String getCategory() {
        return "ApplicationStatus";
    }

    @DBusBoundProperty(access = DBusProperty.Access.READ)
    default String getIconName() {
        return "application-x-executable";
    }

    @DBusBoundProperty(access = DBusProperty.Access.READ)
    default String getTitle() {
        return "PCPanel";
    }

    @DBusBoundProperty(access = DBusProperty.Access.READ)
    default boolean getItemIsMenu() {
        return true;
    }

    /**
     * Double click
     *
     * @param x
     * @param y
     */
    void Activate(int x, int y);

    /**
     * Right click
     *
     * @param x
     * @param y
     */
    void ContextMenu(int x, int y);

    /**
     * Called when user middle-clicks the tray icon.
     *
     * @param x screen x coordinate
     * @param y screen y coordinate
     */
    void SecondaryActivate(int x, int y);

    /**
     * Called when user scrolls over the tray icon.
     *
     * @param delta       scroll amount
     * @param orientation horizontal or vertical
     */
    void Scroll(int delta, String orientation);
}
