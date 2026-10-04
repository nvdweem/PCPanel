package com.getpcpanel.alerts.platform.linux;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.PointerByReference;

/**
 * Minimal JNA bindings to read X11 window properties for {@link LinuxAttentionMonitor} and {@link LinuxWindowTitles}. Atoms and the root window
 * come from Xlib; window properties are read through the same connection's XCB side, which hands a failed request
 * back as an error value. Xlib would report it to its error handler instead, and the default one ends the process,
 * which a window closing while it is read would trigger; a custom handler would be a JNA callback, which the native
 * image cannot run.
 *
 * <p>An XCB cookie is a struct of one 32-bit sequence number, passed and returned by value; on the x86-64 and
 * AArch64 calling conventions that travels exactly like an {@code int}, so it is declared as one.
 *
 * <p>Each interface calls {@code Native.load} in its initializer, so it must be {@code --initialize-at-run-time} and
 * registered in {@code proxy-config.json} for the native image.
 */
final class LinuxXcb {
    private LinuxXcb() {
    }

    interface X11 extends Library {
        X11 INSTANCE = Native.load("X11", X11.class);

        Pointer XOpenDisplay(String displayName);

        long XInternAtom(Pointer display, String name, boolean onlyIfExists);

        long XDefaultRootWindow(Pointer display);

        int XCloseDisplay(Pointer display);
    }

    interface X11Xcb extends Library {
        X11Xcb INSTANCE = Native.load("X11-xcb", X11Xcb.class);

        Pointer XGetXCBConnection(Pointer display);
    }

    interface Xcb extends Library {
        Xcb INSTANCE = Native.load("xcb", Xcb.class);

        /** Any property type ({@code XCB_GET_PROPERTY_TYPE_ANY}). */
        int ANY = 0;

        int xcb_get_property(Pointer connection, byte delete, int window, int property, int type, int longOffset, int longLength);

        /** The reply, to free with {@code free}; null when the request failed, with the error (also to free) in {@code error}. */
        Pointer xcb_get_property_reply(Pointer connection, int cookie, PointerByReference error);

        Pointer xcb_get_property_value(Pointer reply);

        /** The value's length in bytes. */
        int xcb_get_property_value_length(Pointer reply);

        /** Non-zero once the connection has broken (the X server went away); it never recovers. */
        int xcb_connection_has_error(Pointer connection);
    }

    /**
     * Whether the connection has broken (or cannot be asked). Its display must then not be passed to
     * {@code XCloseDisplay}: that reaches Xlib's I/O error handler, whose default ends the process.
     */
    static boolean broken(Pointer connection) {
        try {
            return Xcb.INSTANCE.xcb_connection_has_error(connection) != 0;
        } catch (Throwable t) {
            return true;
        }
    }

    /** {@code XCloseDisplay}, unless {@code connection} (the display's) has broken; then the display is only dropped. */
    static void closeDisplay(Pointer display, Pointer connection) {
        if (!broken(connection)) {
            X11.INSTANCE.XCloseDisplay(display);
        }
    }

    /** The 32-bit items of a property (windows, atoms, cardinals); empty when it is missing or the window is gone. */
    static int[] property32(Pointer connection, int window, int property) {
        var error = new PointerByReference();
        var cookie = Xcb.INSTANCE.xcb_get_property(connection, (byte) 0, window, property, Xcb.ANY, 0, 4096);
        var reply = Xcb.INSTANCE.xcb_get_property_reply(connection, cookie, error);
        if (error.getValue() != null) {
            Native.free(Pointer.nativeValue(error.getValue()));
        }
        if (reply == null) {
            return new int[0];
        }
        try {
            var format = reply.getByte(1);
            var length = Xcb.INSTANCE.xcb_get_property_value_length(reply);
            if (format != 32 || length <= 0) {
                return new int[0];
            }
            return Xcb.INSTANCE.xcb_get_property_value(reply).getIntArray(0, length / 4);
        } finally {
            Native.free(Pointer.nativeValue(reply));
        }
    }

    /** A property's bytes as text (8-bit format), such as {@code WM_CLASS}; empty when missing. */
    static String property8(Pointer connection, int window, int property) {
        var error = new PointerByReference();
        var cookie = Xcb.INSTANCE.xcb_get_property(connection, (byte) 0, window, property, Xcb.ANY, 0, 256);
        var reply = Xcb.INSTANCE.xcb_get_property_reply(connection, cookie, error);
        if (error.getValue() != null) {
            Native.free(Pointer.nativeValue(error.getValue()));
        }
        if (reply == null) {
            return "";
        }
        try {
            var length = Xcb.INSTANCE.xcb_get_property_value_length(reply);
            if (reply.getByte(1) != 8 || length <= 0) {
                return "";
            }
            return new String(Xcb.INSTANCE.xcb_get_property_value(reply).getByteArray(0, length), StandardCharsets.UTF_8);
        } finally {
            Native.free(Pointer.nativeValue(reply));
        }
    }

    /**
     * The window's program file name from its process ({@code _NET_WM_PID}): the executable, else the process name in
     * {@code /proc/<pid>/comm}, else its window class (a sandboxed app's pid may not resolve).
     */
    static Optional<String> program(Pointer connection, int window, int pidAtom, int wmClassAtom) {
        var pid = property32(connection, window, pidAtom);
        if (pid.length > 0 && pid[0] > 0) {
            var proc = Path.of("/proc", String.valueOf(pid[0]));
            try {
                var exe = Files.readSymbolicLink(proc.resolve("exe")).getFileName();
                if (exe != null) {
                    return Optional.of(exe.toString());
                }
            } catch (Exception e) {
                // not ours to read, or gone
            }
            try {
                var comm = Files.readString(proc.resolve("comm")).strip();
                if (!comm.isEmpty()) {
                    return Optional.of(comm);
                }
            } catch (Exception e) {
                // gone
            }
        }
        return windowClass(property8(connection, window, wmClassAtom));
    }

    /** {@code WM_CLASS} holds the instance and the class name, each ending in a NUL; the class is the app's name. */
    static Optional<String> windowClass(String wmClass) {
        var parts = StringUtils.split(wmClass, '\0');
        return Optional.ofNullable(parts.length > 1 ? parts[1] : parts.length == 1 ? parts[0] : null).filter(StringUtils::isNotBlank);
    }
}
