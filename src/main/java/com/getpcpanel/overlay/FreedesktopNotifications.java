package com.getpcpanel.overlay;

import java.util.List;
import java.util.Map;

import org.freedesktop.dbus.annotations.DBusInterfaceName;
import org.freedesktop.dbus.interfaces.DBusInterface;
import org.freedesktop.dbus.types.UInt32;
import org.freedesktop.dbus.types.Variant;

/**
 * Minimal binding to the freedesktop <a
 * href="https://specifications.freedesktop.org/notification-spec/latest/">Desktop Notifications</a>
 * service ({@code org.freedesktop.Notifications} on the session bus), used by {@link
 * LinuxNotifyOverlay} to render the volume overlay as an on-screen notification.
 *
 * <p>This is the compositor-agnostic way to draw an OSD on Wayland (KDE, GNOME, sway/mako, …): unlike
 * the Win32 layered window, no client-side window placement is involved, so it works without the
 * AWT windowing toolkit (unavailable in the native image) and without a Wayland surface protocol that
 * only some compositors implement. KDE/GNOME render the {@code "value"} hint as a volume-style bar.
 */
@DBusInterfaceName("org.freedesktop.Notifications")
public interface FreedesktopNotifications extends DBusInterface {
    /**
     * Show (or replace) a notification. Pass the {@link UInt32} returned by a previous call as {@code
     * replacesId} to update the same notification in place instead of stacking a new one.
     *
     * @return the server-assigned notification id (stable when {@code replacesId} was non-zero).
     */
    UInt32 Notify(String appName, UInt32 replacesId, String appIcon, String summary, String body,
            List<String> actions, Map<String, Variant<?>> hints, int expireTimeout);

    void CloseNotification(UInt32 id);
}
