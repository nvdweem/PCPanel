package com.getpcpanel.sleepdetection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.interfaces.Properties;
import org.freedesktop.dbus.types.Variant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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

    /** Without a system bus socket dbus-java waits out its retries, holding up every other D-Bus connection meanwhile. */
    @Test
    void systemBusOnlyWhenItsSocketIsThere(@TempDir Path dir) throws IOException {
        var socket = Files.createFile(dir.resolve("system_bus_socket"));
        assertTrue(LinuxSystemEventService.systemBusPresent("unix:path=" + socket));
        assertTrue(LinuxSystemEventService.systemBusPresent("unix:path=" + socket + ",guid=0123"));
        assertFalse(LinuxSystemEventService.systemBusPresent("unix:path=" + dir.resolve("missing")), "a sandbox without a system-bus grant");
        assertTrue(LinuxSystemEventService.systemBusPresent("unix:abstract=/tmp/dbus-x"), "not a path: let dbus-java try");
    }
}
