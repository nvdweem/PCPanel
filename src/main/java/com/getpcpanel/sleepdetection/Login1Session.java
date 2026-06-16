package com.getpcpanel.sleepdetection;

import org.freedesktop.dbus.annotations.DBusInterfaceName;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.interfaces.DBusInterface;
import org.freedesktop.dbus.messages.DBusSignal;

/**
 * Minimal binding to systemd-logind's {@code org.freedesktop.login1.Session} (system bus), used only
 * for its {@link Lock}/{@link Unlock} signals. Best-effort: these fire when a session is locked or
 * unlocked via logind (e.g. {@code loginctl lock-session} or a desktop that integrates with it); not
 * every Linux desktop routes screen locking through logind.
 */
@DBusInterfaceName("org.freedesktop.login1.Session")
public interface Login1Session extends DBusInterface {
    class Lock extends DBusSignal {
        public Lock(String path) throws DBusException {
            super(path);
        }
    }

    class Unlock extends DBusSignal {
        public Unlock(String path) throws DBusException {
            super(path);
        }
    }
}
