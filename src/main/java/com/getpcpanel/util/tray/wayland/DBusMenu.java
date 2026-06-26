package com.getpcpanel.util.tray.wayland;

import java.util.List;

import org.freedesktop.dbus.annotations.DBusBoundProperty;
import org.freedesktop.dbus.annotations.DBusInterfaceName;
import org.freedesktop.dbus.annotations.DBusProperty.Access;
import org.freedesktop.dbus.interfaces.DBusInterface;
import org.freedesktop.dbus.types.UInt32;
import org.freedesktop.dbus.types.Variant;

/**
 * The {@code com.canonical.dbusmenu} interface that a StatusNotifierItem host (e.g. KDE Plasma) calls
 * to render the tray icon's context menu. The {@link StatusNotifierItem#getMenu()} property points the
 * host at the object that implements this; the host then reads the layout and reports clicks back
 * through {@link #Event}.
 *
 * <p>Only the methods a host needs for a small static menu are implemented (GetLayout,
 * GetGroupProperties, GetProperty, Event, AboutToShow); the optional update signals are omitted because
 * the menu never changes.
 *
 * @see <a href="https://github.com/AyatanaIndicators/libdbusmenu/blob/master/libdbusmenu-glib/dbus-menu.xml">dbusmenu spec</a>
 */
@DBusInterfaceName("com.canonical.dbusmenu")
public interface DBusMenu extends DBusInterface {
    @DBusBoundProperty(access = Access.READ)
    default UInt32 getVersion() {
        return new UInt32(3);
    }

    @DBusBoundProperty(access = Access.READ)
    default String getTextDirection() {
        return "ltr";
    }

    @DBusBoundProperty(access = Access.READ)
    default String getStatus() {
        return "normal";
    }

    @DBusBoundProperty(access = Access.READ)
    default List<String> getIconThemePath() {
        return List.of();
    }

    /** Returns the menu tree rooted at {@code parentId} (the host passes 0 and depth -1 for the whole menu). */
    MenuLayoutReturn<UInt32, MenuItemLayout> GetLayout(int parentId, int recursionDepth, List<String> propertyNames);

    /** Returns the requested properties for each of the given item ids. */
    List<MenuItemProperties> GetGroupProperties(List<Integer> ids, List<String> propertyNames);

    /** Returns a single property of a single item. */
    Variant<?> GetProperty(int id, String name);

    /** The host reports a UI event here; {@code eventId} is {@code "clicked"} when an item is activated. */
    void Event(int id, String eventId, Variant<?> data, UInt32 timestamp);

    /** Called before the host shows the (sub)menu; {@code true} would mean the layout must be re-fetched. */
    boolean AboutToShow(int id);
}
