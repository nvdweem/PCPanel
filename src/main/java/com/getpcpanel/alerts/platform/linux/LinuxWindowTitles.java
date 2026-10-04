package com.getpcpanel.alerts.platform.linux;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;

import com.getpcpanel.alerts.WindowTitles;
import com.getpcpanel.platform.LinuxBuild;
import com.sun.jna.Pointer;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.log4j.Log4j2;

/**
 * The titles of the X11 windows the window manager lists ({@code _NET_CLIENT_LIST}), by their program
 * ({@link LinuxXcb#program}). Works on X11 sessions, and on Wayland for apps that run through XWayland; native Wayland
 * windows are not listed. The display connection is opened on the first look and kept for the next, until it breaks
 * (then it is opened again after a pause) or titles are no longer needed ({@link #release()}). A failure while reading
 * drops the connection and is thrown.
 */
@Log4j2
@LinuxBuild
@ApplicationScoped
class LinuxWindowTitles implements WindowTitles {
    /** How long to wait after the X display could not be opened. */
    private static final long RETRY_MS = 30_000;

    @Nullable private Pointer display;
    @Nullable private Connection connection;
    /** When the display last failed to open or broke; 0 when it has not. */
    private long failedAt;

    private record Connection(Pointer xcb, int root, int clientList, int netWmName, int wmName, int pid, int wmClass) {
    }

    @Override
    public synchronized Map<String, List<String>> titles() {
        var result = new HashMap<String, List<String>>();
        try {
            var c = connect();
            if (c == null) {
                return result;
            }
            if (LinuxXcb.broken(c.xcb())) {
                log.debug("Window titles: the X connection broke");
                broke();
                return result;
            }
            for (var window : LinuxXcb.property32(c.xcb(), c.root(), c.clientList())) {
                var title = title(c, window);
                if (title.isEmpty()) {
                    continue;
                }
                LinuxXcb.program(c.xcb(), window, c.pid(), c.wmClass())
                        .map(name -> StringUtils.removeEndIgnoreCase(name, ".exe").toLowerCase(Locale.ROOT))
                        .ifPresent(name -> result.computeIfAbsent(name, k -> new ArrayList<>()).add(title));
            }
        } catch (Throwable t) {
            broke();
            throw t;
        }
        return result;
    }

    /** {@code _NET_WM_NAME} (UTF-8), else the older {@code WM_NAME}. */
    private static String title(Connection c, int window) {
        var name = LinuxXcb.property8(c.xcb(), window, c.netWmName());
        return name.isEmpty() ? LinuxXcb.property8(c.xcb(), window, c.wmName()) : name;
    }

    @Nullable
    private Connection connect() {
        if (connection != null) {
            return connection;
        }
        if (StringUtils.isBlank(System.getenv("DISPLAY")) || System.currentTimeMillis() - failedAt < RETRY_MS) {
            return null;
        }
        var x = LinuxXcb.X11.INSTANCE;
        var opened = x.XOpenDisplay(null);
        if (opened == null) {
            log.debug("Window titles: no X display");
            failedAt = System.currentTimeMillis();
            return null;
        }
        display = opened;
        connection = new Connection(LinuxXcb.X11Xcb.INSTANCE.XGetXCBConnection(opened), (int) x.XDefaultRootWindow(opened),
                (int) x.XInternAtom(opened, "_NET_CLIENT_LIST", false), (int) x.XInternAtom(opened, "_NET_WM_NAME", false),
                (int) x.XInternAtom(opened, "WM_NAME", false),
                (int) x.XInternAtom(opened, "_NET_WM_PID", false), (int) x.XInternAtom(opened, "WM_CLASS", false));
        return connection;
    }

    private void broke() {
        release();
        failedAt = System.currentTimeMillis();
    }

    /** Closes the display ({@link LinuxXcb#closeDisplay}: a broken connection is only dropped). */
    @Override
    @PreDestroy
    public synchronized void release() {
        var c = connection;
        var d = display;
        connection = null;
        display = null;
        if (d != null && c != null) {
            LinuxXcb.closeDisplay(d, c.xcb());
        }
    }
}
