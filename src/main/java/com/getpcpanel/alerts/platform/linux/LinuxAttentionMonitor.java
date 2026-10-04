package com.getpcpanel.alerts.platform.linux;

import java.util.HashSet;
import java.util.Set;

import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;

import com.getpcpanel.alerts.TaskbarFlashEvent;
import com.getpcpanel.platform.LinuxBuild;
import com.getpcpanel.profile.SaveService;
import com.getpcpanel.profile.dto.NotificationAlert.AlertTrigger;
import com.sun.jna.Pointer;

import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import lombok.extern.log4j.Log4j2;

/**
 * The Linux counterpart of the taskbar flash: an X11 window that asks for attention, marked "demands attention" or
 * "urgent" (how chat apps flag a new message), reported as a {@link TaskbarFlashEvent} with its program's file name.
 * Polls once a second, only while a taskbar-flash notification light is configured. Works on X11 sessions, and on
 * Wayland for apps that run through XWayland (Electron apps such as Discord usually do); native Wayland windows have
 * no shared way to ask for attention that another program can see.
 */
@Log4j2
@Startup
@LinuxBuild
@ApplicationScoped
public class LinuxAttentionMonitor {
    private static final long POLL_MS = 1_000;
    /** How often to look whether a taskbar-flash light was added. */
    private static final long IDLE_MS = 2_000;
    /** How long to wait after the X display could not be opened. */
    private static final long RETRY_MS = 30_000;
    /** {@code XUrgencyHint} in the {@code WM_HINTS} flags. */
    private static final int URGENCY_HINT = 1 << 8;

    @Inject SaveService save;
    @Inject Event<Object> eventBus;

    private volatile boolean running;
    @Nullable private Thread thread;

    @PostConstruct
    void start() {
        running = true;
        thread = new Thread(this::run, "linux-attention-monitor");
        thread.setDaemon(true);
        thread.start();
    }

    @PreDestroy
    void stop() {
        running = false;
        if (thread != null) {
            thread.interrupt();
        }
    }

    private void run() {
        while (running) {
            var wait = IDLE_MS;
            if (wanted() && StringUtils.isNotBlank(System.getenv("DISPLAY"))) {
                try {
                    if (!watch()) {
                        wait = RETRY_MS;
                    }
                } catch (Throwable t) {
                    log.debug("Attention monitor stopped: {}", t.toString());
                    wait = RETRY_MS;
                }
            }
            if (!sleep(wait)) {
                return;
            }
        }
    }

    private boolean wanted() {
        try {
            return save.get().getNotificationAlerts().stream().anyMatch(a -> !a.disabled() && a.trigger() == AlertTrigger.TASKBAR_FLASH);
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * Polls until no taskbar-flash light is configured any more; false when there is no X display to watch or the
     * connection to it broke.
     */
    private boolean watch() {
        var display = LinuxXcb.X11.INSTANCE.XOpenDisplay(null);
        if (display == null) {
            log.debug("Attention monitor: no X display");
            return false;
        }
        var connection = LinuxXcb.X11Xcb.INSTANCE.XGetXCBConnection(display);
        try {
            var x = LinuxXcb.X11.INSTANCE;
            var root = (int) x.XDefaultRootWindow(display);
            var atoms = new Atoms((int) x.XInternAtom(display, "_NET_CLIENT_LIST", false), (int) x.XInternAtom(display, "_NET_WM_STATE", false),
                    (int) x.XInternAtom(display, "_NET_WM_STATE_DEMANDS_ATTENTION", false), (int) x.XInternAtom(display, "WM_HINTS", false),
                    (int) x.XInternAtom(display, "_NET_WM_PID", false), (int) x.XInternAtom(display, "WM_CLASS", false));
            log.info("Watching X11 windows for attention requests (taskbar-flash notification lights)");
            Set<Integer> before = new HashSet<>();
            while (running && wanted()) {
                if (LinuxXcb.broken(connection)) {
                    log.debug("Attention monitor: the X connection broke");
                    return false;
                }
                var now = new HashSet<Integer>();
                for (var window : LinuxXcb.property32(connection, root, atoms.clientList())) {
                    if (wantsAttention(connection, window, atoms)) {
                        now.add(window);
                        if (!before.contains(window)) {
                            LinuxXcb.program(connection, window, atoms.pid(), atoms.wmClass()).ifPresent(exe -> {
                                log.debug("{} asks for attention", exe);
                                eventBus.fire(new TaskbarFlashEvent(exe));
                            });
                        }
                    }
                }
                before = now;
                if (!sleep(POLL_MS)) {
                    break;
                }
            }
            return true;
        } finally {
            LinuxXcb.closeDisplay(display, connection);
        }
    }

    private record Atoms(int clientList, int state, int demandsAttention, int hints, int pid, int wmClass) {
    }

    private static boolean wantsAttention(Pointer connection, int window, Atoms atoms) {
        for (var state : LinuxXcb.property32(connection, window, atoms.state())) {
            if (state == atoms.demandsAttention()) {
                return true;
            }
        }
        var hints = LinuxXcb.property32(connection, window, atoms.hints());
        return hints.length > 0 && (hints[0] & URGENCY_HINT) != 0;
    }

    private boolean sleep(long ms) {
        try {
            Thread.sleep(ms);
            return running;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
