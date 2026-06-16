package com.getpcpanel.sleepdetection;

import org.freedesktop.dbus.annotations.DBusInterfaceName;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.interfaces.DBusInterface;
import org.freedesktop.dbus.messages.DBusSignal;

/**
 * Minimal binding to systemd-logind's {@code org.freedesktop.login1.Manager} (system bus), used only
 * for its {@link PrepareForSleep} signal.
 *
 * @see <a href="https://www.freedesktop.org/software/systemd/man/latest/org.freedesktop.login1.html">login1 D-Bus API</a>
 */
@DBusInterfaceName("org.freedesktop.login1.Manager")
public interface Login1Manager extends DBusInterface {
    /** Emitted with {@code start=true} just before the system suspends/hibernates and {@code start=false} after it resumes. */
    class PrepareForSleep extends DBusSignal {
        public final boolean start;

        public PrepareForSleep(String path, boolean start) throws DBusException {
            super(path, start);
            this.start = start;
        }
    }
}
