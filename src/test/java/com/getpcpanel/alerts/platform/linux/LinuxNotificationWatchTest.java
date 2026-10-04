package com.getpcpanel.alerts.platform.linux;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import com.getpcpanel.util.os.ProcessHelper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LinuxNotificationWatchTest {
    private static final String NOTIFY = """
            method call time=1.0 sender=:1.50 -> destination=org.freedesktop.Notifications serial=7 path=/org/freedesktop/Notifications; interface=org.freedesktop.Notifications; member=Notify
               string "discord"
               uint32 0
               string "icon"
               string "New message"
               string "hi"
               array [
               ]
               array [
                  dict entry(
                     string "urgency"
                     variant                byte 1
                  )
               ]
               int32 -1
            method return time=1.1 sender=:1.20 -> destination=:1.50 serial=90 reply_serial=7
               uint32 42
            """;

    private LinuxNotificationWatch sut;

    @BeforeEach
    void setUp() {
        sut = new LinuxNotificationWatch(null, false);
    }

    private void feed(String trace) {
        trace.lines().forEach(sut::onLine);
    }

    private static String closed(int id, int reason) {
        return """
                signal time=3.0 sender=:1.20 -> destination=(null destination) serial=92 path=/org/freedesktop/Notifications; interface=org.freedesktop.Notifications; member=NotificationClosed
                   uint32 %d
                   uint32 %d
                """.formatted(id, reason);
    }

    @Test
    void aDismissedNotificationIsGone() {
        feed(NOTIFY);
        assertEquals(Set.of("discord"), sut.appsWithNotifications());
        feed(closed(42, 2));
        assertTrue(sut.appsWithNotifications().isEmpty());
    }

    @Test
    void anExpiredNotificationStaysInTheList() {
        feed(NOTIFY);
        feed(closed(42, 1));
        assertEquals(Set.of("discord"), sut.appsWithNotifications());
    }

    @Test
    void aReplyToAnotherCallIsIgnored() {
        feed("""
                method return time=2.2 sender=:1.9 -> destination=:1.61 serial=5 reply_serial=4
                   uint32 999
                """);
        assertTrue(sut.appsWithNotifications().isEmpty());
    }

    @Test
    void namesAreLowerCaseAndRememberedAsSources() {
        feed(NOTIFY.replace("\"discord\"", "\"Slack\""));
        assertEquals(Set.of("slack"), sut.appsWithNotifications());
        feed(closed(42, 3));
        assertTrue(sut.appsWithNotifications().isEmpty());
        assertEquals(Set.of("slack"), sut.sources());
    }

    @Test
    void eachNewNotificationCountsHigher() {
        feed(NOTIFY);
        var first = sut.newestNotifications().get("discord");
        feed(NOTIFY.replace("serial=7", "serial=8").replace("reply_serial=7", "reply_serial=8").replace("uint32 42", "uint32 43"));
        assertTrue(sut.newestNotifications().get("discord") > first);
    }

    @Test
    void expiredNotificationsAreKeptAsOneNumberPerApp() throws Exception {
        long previous = -1;
        for (var i = 0; i < 50; i++) {
            feed(NOTIFY.replace("serial=7", "serial=" + (100 + i)).replace("reply_serial=7", "reply_serial=" + (100 + i))
                       .replace("uint32 42", "uint32 " + (1000 + i)));
            feed(closed(1000 + i, 1));
            var newest = sut.newestNotifications().get("discord");
            assertTrue(newest > previous, "each expired notification still counts as newer");
            previous = newest;
        }
        assertEquals(Set.of("discord"), sut.appsWithNotifications());
        assertEquals(1, sut.tracked());
    }

    @Test
    void theMonitorRunsOnlyWhileAskedAndStopsOnShutdown() throws Exception {
        var helper = new FakeProcessHelper();
        var watch = new LinuxNotificationWatch(helper, false);
        var now = System.currentTimeMillis();
        watch.supervise(now);
        assertTrue(helper.started.isEmpty(), "nobody asked yet");

        watch.appsWithNotifications();
        watch.supervise(now);
        assertEquals(1, helper.started.size());
        assertEquals(LinuxNotificationWatch.COMMAND, helper.commands.getFirst());
        helper.started.getFirst().write(NOTIFY);
        waitFor(() -> watch.appsWithNotifications().contains("discord"));

        watch.supervise(System.currentTimeMillis() + LinuxNotificationWatch.IDLE_STOP_MS + 1);
        assertFalse(helper.started.getFirst().alive, "stopped once nobody asks");
        assertFalse(watch.monitoring());
        assertEquals(0, watch.tracked(), "what it followed is forgotten");

        watch.appsWithNotifications();
        watch.supervise(System.currentTimeMillis());
        assertEquals(2, helper.started.size());
        watch.stop();
        assertFalse(helper.started.get(1).alive, "shutdown stops the monitor");
    }

    @Test
    void aMonitorThatEndsIsRestartedAfterADelay() {
        var helper = new FakeProcessHelper();
        var watch = new LinuxNotificationWatch(helper, false);
        watch.appsWithNotifications();
        var now = System.currentTimeMillis();
        watch.supervise(now);
        helper.started.getFirst().alive = false;
        watch.supervise(now + 100);
        assertEquals(1, helper.started.size(), "not straight away");
        watch.appsWithNotifications();
        watch.supervise(now + 5_000);
        assertEquals(2, helper.started.size());
        watch.stop();
    }

    private static void waitFor(java.util.function.BooleanSupplier condition) throws InterruptedException {
        var until = System.currentTimeMillis() + 5_000;
        while (!condition.getAsBoolean()) {
            assertTrue(System.currentTimeMillis() < until, "timed out");
            Thread.sleep(10);
        }
    }

    private static final class FakeProcessHelper extends ProcessHelper {
        final List<FakeProcess> started = new ArrayList<>();
        final List<List<String>> commands = new ArrayList<>();

        @Override
        public Process startReading(String... command) {
            commands.add(List.of(command));
            var process = new FakeProcess();
            started.add(process);
            return process;
        }
    }

    private static final class FakeProcess extends Process {
        volatile boolean alive = true;
        private final PipedOutputStream feed = new PipedOutputStream();
        private final PipedInputStream stdout;

        FakeProcess() {
            try {
                stdout = new PipedInputStream(feed, 1 << 16);
            } catch (java.io.IOException e) {
                throw new IllegalStateException(e);
            }
        }

        void write(String text) throws java.io.IOException {
            feed.write(text.getBytes(StandardCharsets.UTF_8));
            feed.flush();
        }

        @Override
        public OutputStream getOutputStream() {
            return OutputStream.nullOutputStream();
        }

        @Override
        public InputStream getInputStream() {
            return stdout;
        }

        @Override
        public InputStream getErrorStream() {
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public int waitFor() {
            return 0;
        }

        @Override
        public int exitValue() {
            return 0;
        }

        @Override
        public boolean isAlive() {
            return alive;
        }

        @Override
        public Stream<ProcessHandle> descendants() {
            return Stream.empty();
        }

        @Override
        public void destroy() {
            alive = false;
            try {
                feed.close();
            } catch (java.io.IOException e) {
                // already closed
            }
        }
    }
}
