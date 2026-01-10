package com.getpcpanel.util.tray;

import org.freedesktop.dbus.annotations.DBusInterfaceName;
import org.freedesktop.dbus.interfaces.DBusInterface;

/**
 * D-Bus interface for StatusNotifierItem (SNI) protocol.
 * Used by Wayland compositors like Sway for system tray icons.
 *
 * @see <a href="https://www.freedesktop.org/wiki/Specifications/StatusNotifierItem/">SNI Specification</a>
 */
@DBusInterfaceName("org.kde.StatusNotifierItem")
public interface StatusNotifierItem extends DBusInterface {

    // Required properties exposed as getters

    /** Application category: ApplicationStatus, Communications, SystemServices, Hardware */
    String getCategory();

    /** Unique application identifier */
    String getId();

    /** Current status: Passive, Active, or NeedsAttention */
    String getStatus();

    /** Freedesktop-compliant icon name or absolute path */
    String getIconName();

    /** Human-readable application title */
    String getTitle();

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
