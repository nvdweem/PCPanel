package com.getpcpanel.util.tray.wayland;

import java.util.Map;

import org.freedesktop.dbus.annotations.DBusInterfaceName;
import org.freedesktop.dbus.interfaces.DBusInterface;
import org.freedesktop.dbus.types.Variant;

/**
 * The xdg-desktop-portal Background portal ({@code org.freedesktop.portal.Background} on
 * {@code org.freedesktop.portal.Desktop} at {@code /org/freedesktop/portal/desktop}). Only {@link #SetStatus} is
 * bound: it attaches a one-line message to this Flatpak instance's entry in the desktop's list of background apps.
 */
@DBusInterfaceName("org.freedesktop.portal.Background")
public interface PortalBackground extends DBusInterface {
    String BUS_NAME = "org.freedesktop.portal.Desktop";
    String OBJECT_PATH = "/org/freedesktop/portal/desktop";

    /** {@code options} supports {@code message}: a single line of at most 96 characters (the portal rejects longer). */
    void SetStatus(Map<String, Variant<?>> options);
}
