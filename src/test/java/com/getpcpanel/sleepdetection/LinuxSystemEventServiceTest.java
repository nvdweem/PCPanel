package com.getpcpanel.sleepdetection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Map;

import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.interfaces.Properties;
import org.freedesktop.dbus.types.Variant;
import org.junit.jupiter.api.Test;

class LinuxSystemEventServiceTest {
    private static final String SESSION = "/org/freedesktop/login1/session/_32";

    private static Properties.PropertiesChanged changed(String path, String iface, Map<String, Variant<?>> props) throws DBusException {
        return new Properties.PropertiesChanged(path, iface, props, List.of());
    }

    /** KDE Plasma and GNOME lock on their own and only set LockedHint; logind's Lock signal never comes. */
    @Test
    void followsTheSessionsLockedHint() throws DBusException {
        assertEquals(SystemEventType.locked, LinuxSystemEventService.lockedHintChange(SESSION,
                changed(SESSION, "org.freedesktop.login1.Session", Map.of("LockedHint", new Variant<>(true)))));
        assertEquals(SystemEventType.unlocked, LinuxSystemEventService.lockedHintChange(SESSION,
                changed(SESSION, "org.freedesktop.login1.Session", Map.of("LockedHint", new Variant<>(false)))));
    }

    @Test
    void ignoresOtherSessionsInterfacesAndProperties() throws DBusException {
        assertNull(LinuxSystemEventService.lockedHintChange(SESSION,
                changed("/org/freedesktop/login1/session/c1", "org.freedesktop.login1.Session", Map.of("LockedHint", new Variant<>(true)))),
                "the greeter's or another user's session");
        assertNull(LinuxSystemEventService.lockedHintChange(SESSION,
                changed(SESSION, "org.freedesktop.login1.User", Map.of("LockedHint", new Variant<>(true)))));
        assertNull(LinuxSystemEventService.lockedHintChange(SESSION,
                changed(SESSION, "org.freedesktop.login1.Session", Map.of("IdleHint", new Variant<>(true)))));
    }
}
