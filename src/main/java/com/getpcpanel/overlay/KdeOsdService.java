package com.getpcpanel.overlay;

import org.freedesktop.dbus.annotations.DBusInterfaceName;
import org.freedesktop.dbus.interfaces.DBusInterface;

/**
 * Binding to KDE Plasma's on-screen-display service ({@code org.kde.osdService}, owned by
 * {@code org.kde.plasmashell} at {@code /org/kde/osdService}). This is the same OSD Plasma shows for
 * its own volume keys: calling {@link #volumeChanged} repeatedly updates one OSD in place, in real
 * time, and it auto-hides — exactly the behaviour wanted for the volume overlay, rendered natively by
 * the compositor (so it works on Wayland without any client-side window placement or AWT).
 *
 * <p>Used by {@link LinuxOverlay}, which is the Linux overlay on KDE; other desktops have no overlay
 * (see {@link LinuxOverlay} for why a notification fallback was rejected).
 */
@DBusInterfaceName("org.kde.osdService")
public interface KdeOsdService extends DBusInterface {
    /** Show/refresh the native volume OSD with the given level (0..100). */
    void volumeChanged(int percent);

    /** Show a brief text OSD with a themed icon — used for non-volume (button) overlays. */
    void showText(String icon, String text);
}
