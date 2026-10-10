package com.getpcpanel.alerts.platform.linux;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;

import com.getpcpanel.alerts.NotificationWatch;
import com.getpcpanel.platform.LinuxBuild;
import com.getpcpanel.util.os.ProcessHelper;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.log4j.Log4j2;

/**
 * Desktop notifications on Linux, followed on the session bus with {@code dbus-monitor}: each {@code Notify} call names
 * its app, the call's reply carries the notification's id, and {@code NotificationClosed} says when it is gone. One
 * that only expired still counts, as desktops keep those in their notification list; dismissed or withdrawn ones do
 * not. Expired ones are kept as one number per app (its newest), so what is kept grows with the apps, not the
 * notifications.
 *
 * <p>{@code dbus-monitor} runs only while someone asks: it starts at the first question and stops once nobody has
 * asked for {@link #IDLE_STOP_MS} (the notification lights ask every two seconds while one is switched on, the source
 * picker when it opens). It restarts with a growing delay when it ends on its own. What it followed is forgotten when
 * it stops; notifications from before it started are not known. The match rules take only {@code Notify} calls,
 * {@code NotificationClosed} signals and the replies sent by the connection that owns the notification service. In the
 * Flatpak, {@code dbus-monitor} is a shim that runs the host's ({@code packaging/linux/flatpak/dbus-monitor-wrapper.sh}).
 */
@Log4j2
@LinuxBuild
@ApplicationScoped
public class LinuxNotificationWatch implements NotificationWatch {
    static final List<String> COMMAND = List.of("dbus-monitor", "--session",
            "type='method_call',interface='org.freedesktop.Notifications',member='Notify'",
            "type='method_return',sender='org.freedesktop.Notifications'",
            "type='signal',interface='org.freedesktop.Notifications',member='NotificationClosed'");
    /** How long after the last question the monitor keeps running. */
    static final long IDLE_STOP_MS = 10_000;
    private static final long SUPERVISE_MS = 1_000;
    private static final long FIRST_RESTART_MS = 2_000;
    private static final long MAX_RESTART_MS = 60_000;
    /** {@code NotificationClosed} reason: the notification expired. */
    private static final String EXPIRED = "uint32 1";
    private static final int MAX_WAITING = 100;
    private static final boolean IN_FLATPAK = System.getenv("FLATPAK_ID") != null;
    private static final String MISSING = "Lights for notifications need dbus-monitor, which isn't available" + (IN_FLATPAK ? " on this computer" : "")
            + ": install the dbus-bin package (Debian/Ubuntu) or dbus-tools (Fedora), then open this page again. Taskbar flashes,"
            + " window titles and the microphone light work without it.";

    @Inject ProcessHelper processHelper;
    /** Whether a thread of its own starts and stops the monitor; tests call {@link #supervise} themselves. */
    private final boolean supervised;

    /** Every notification so far, numbered in order. */
    private long calls;
    /** Open notifications: id → app and its number. */
    private final Map<Long, Open> open = new HashMap<>();
    /** Apps with expired notifications, each with the number of its newest. */
    private final Map<String, Long> expired = new HashMap<>();
    /** {@code Notify} calls waiting for their reply: caller and serial → app. */
    private final Map<String, String> waiting = new HashMap<>();
    private final Set<String> seen = new LinkedHashSet<>();
    /** What the next argument line belongs to. */
    private Expect expect = Expect.NOTHING;
    @Nullable private String expectApp;
    private long closing;

    private volatile long askedAt = Long.MIN_VALUE / 2;
    private volatile boolean stopped;
    @Nullable private volatile Thread supervisor;
    @Nullable private volatile Process process;
    /** The monitor being stopped on purpose, so its ending is not taken for dbus-monitor being unusable. */
    @Nullable private volatile Process stopping;
    /** Why notifications can't be followed, for the settings page; {@code null} while they can (or nobody asked yet). */
    @Nullable private volatile String unavailable;
    // Only touched by supervise(), which runs on one thread.
    private long processStartedAt;
    private long restartAt;
    private long backoff = FIRST_RESTART_MS;

    private record Open(String app, long number) {
    }

    private enum Expect { NOTHING, APP, ID, CLOSED, REASON }

    public LinuxNotificationWatch() {
        this(null, true);
    }

    LinuxNotificationWatch(@Nullable ProcessHelper processHelper, boolean supervised) {
        this.processHelper = processHelper;
        this.supervised = supervised;
    }

    @Override
    public Set<String> appsWithNotifications() {
        return newestNotifications().keySet();
    }

    @Override
    public Map<String, Long> newestNotifications() {
        asked();
        synchronized (this) {
            var result = new HashMap<>(expired);
            open.values().forEach(o -> result.merge(o.app(), o.number(), Math::max));
            return result;
        }
    }

    @Override
    public Set<String> sources() {
        asked();
        synchronized (this) {
            return Set.copyOf(seen);
        }
    }

    /** How many entries are kept: the open notifications plus one per app with expired ones. */
    synchronized int tracked() {
        return open.size() + expired.size();
    }

    boolean monitoring() {
        return process != null;
    }

    private void asked() {
        askedAt = System.currentTimeMillis();
        if (supervised && processHelper != null && supervisor == null) {
            startSupervisor();
        }
    }

    private synchronized void startSupervisor() {
        if (supervisor != null || stopped) {
            return;
        }
        var t = new Thread(this::superviseLoop, "linux-notification-watch");
        t.setDaemon(true);
        supervisor = t;
        t.start();
    }

    @PreDestroy
    void stop() {
        stopped = true;
        var t = supervisor;
        if (t != null) {
            t.interrupt();
        }
        stopProcess();
    }

    private void superviseLoop() {
        while (!stopped) {
            try {
                supervise(System.currentTimeMillis());
                Thread.sleep(SUPERVISE_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (RuntimeException e) {
                log.debug("Notification watch supervisor failed", e);
            }
        }
        stopProcess();
    }

    /** Starts the monitor while it is asked for, stops it when it is not, and restarts it after it ended. */
    void supervise(long now) {
        var wanted = !stopped && now - askedAt < IDLE_STOP_MS;
        var running = process;
        if (running != null && !running.isAlive()) {
            process = null;
            reset();
            backoff = now - processStartedAt > MAX_RESTART_MS ? FIRST_RESTART_MS : Math.min(backoff * 2, MAX_RESTART_MS);
            restartAt = now + backoff;
            log.debug("dbus-monitor ended (exit {}); notification lights miss notifications until it is back", running.exitValue());
        } else if (running != null && !wanted) {
            stopProcess();
            backoff = FIRST_RESTART_MS;
            restartAt = 0;
        }
        if (process == null && wanted && now >= restartAt) {
            start(now);
        }
    }

    private void start(long now) {
        try {
            var started = processHelper.startReading(COMMAND.toArray(String[]::new));
            process = started;
            processStartedAt = now;
            var reader = new Thread(() -> read(started), "linux-notification-watch-reader");
            reader.setDaemon(true);
            reader.start();
        } catch (IOException e) {
            if (unavailable == null) {
                log.warn("dbus-monitor could not be started; notification lights need it (package dbus-tools on Fedora, dbus-bin on Debian/Ubuntu): {}", e.toString());
            }
            unavailable = MISSING;
            restartAt = now + backoff;
            backoff = Math.min(backoff * 2, MAX_RESTART_MS);
        }
    }

    private void read(Process from) {
        var any = false;
        try (var reader = new BufferedReader(new InputStreamReader(from.getInputStream(), Charset.defaultCharset()))) {
            String line;
            //noinspection NestedAssignment
            while ((line = reader.readLine()) != null) {
                if (process != from) {
                    return; // stopped; what it still writes no longer counts
                }
                any = true;
                unavailable = null;
                onLine(line);
            }
        } catch (IOException e) {
            // ended or stopped
        }
        if (!any && stopping != from && !stopped) {
            // It ended without saying anything: not installed (in the Flatpak the host's is run, and flatpak-spawn starts
            // fine without it) or unable to reach the session bus.
            if (unavailable == null) {
                log.warn("dbus-monitor ended without any output; notification lights need it (package dbus-tools on Fedora, dbus-bin on Debian/Ubuntu{})",
                        IN_FLATPAK ? ", installed on the host" : "");
            }
            unavailable = MISSING;
        }
    }

    @Override
    @Nullable
    public String unavailable() {
        return unavailable;
    }

    private void stopProcess() {
        var running = process;
        process = null;
        if (running != null) {
            stopping = running;
            ProcessHelper.stop(running);
        }
        reset();
    }

    /** What was followed no longer holds once the monitor stopped: closes may have gone by unseen. */
    private synchronized void reset() {
        open.clear();
        expired.clear();
        waiting.clear();
        expect = Expect.NOTHING;
    }

    /** One line of {@code dbus-monitor} output: a message header at the start of the line, its arguments indented. */
    synchronized void onLine(String line) {
        if (!line.startsWith(" ")) {
            header(line);
            return;
        }
        var arg = line.strip();
        var current = expect;
        expect = Expect.NOTHING;
        switch (current) {
            case APP -> {
                var app = StringUtils.removeEnd(StringUtils.removeStart(arg, "string \""), "\"").toLowerCase(Locale.ROOT);
                if (arg.startsWith("string \"") && StringUtils.isNotBlank(app) && expectApp != null) {
                    if (waiting.size() >= MAX_WAITING) {
                        waiting.clear(); // replies that never came (the call failed)
                    }
                    waiting.put(expectApp, app);
                    seen.add(app);
                }
            }
            case ID -> {
                var id = uint(arg);
                if (id != null && expectApp != null) {
                    open.put(id, new Open(expectApp, ++calls));
                }
            }
            case CLOSED -> {
                var id = uint(arg);
                if (id != null) {
                    closing = id;
                    expect = Expect.REASON;
                }
            }
            case REASON -> {
                var closed = open.remove(closing);
                if (closed != null && EXPIRED.equals(arg)) {
                    expired.merge(closed.app(), closed.number(), Math::max);
                }
            }
            case NOTHING -> {
                // an argument nobody asked for
            }
        }
    }

    private void header(String line) {
        expect = Expect.NOTHING;
        expectApp = null;
        var member = field(line, "member=");
        if (line.startsWith("method call") && "Notify".equals(member)) {
            var sender = field(line, "sender=");
            var serial = field(line, "serial=");
            if (sender != null && serial != null) {
                expect = Expect.APP;
                expectApp = sender + " " + serial;
            }
        } else if (line.startsWith("method return")) {
            var destination = field(line, "destination=");
            var replySerial = field(line, "reply_serial=");
            var app = destination == null || replySerial == null ? null : waiting.remove(destination + " " + replySerial);
            if (app != null) {
                expect = Expect.ID;
                expectApp = app;
            }
        } else if (line.startsWith("signal") && "NotificationClosed".equals(member)) {
            expect = Expect.CLOSED;
        }
    }

    /** The value of {@code key} among the header's space-separated fields, without a trailing {@code ;}. */
    @Nullable
    private static String field(String line, String key) {
        for (var word : StringUtils.split(line, ' ')) {
            if (word.startsWith(key)) {
                return StringUtils.removeEnd(word.substring(key.length()), ";");
            }
        }
        return null;
    }

    @Nullable
    private static Long uint(String arg) {
        if (!arg.startsWith("uint32 ")) {
            return null;
        }
        try {
            return Long.parseLong(arg.substring("uint32 ".length()).strip());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
