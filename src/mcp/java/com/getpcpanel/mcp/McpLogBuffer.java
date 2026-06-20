package com.getpcpanel.mcp;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

import org.jboss.logmanager.LogContext;

import io.quarkus.arc.properties.IfBuildProperty;
import io.quarkus.runtime.StartupEvent;

/**
 * In-memory ring buffer of recent WARN/ERROR log records, attached to the root logger at startup.
 * Backs {@code pcpanel_last_error} (and the warning feed) so it works even when file logging is off
 * or rotated away - the original pain point was having to locate and grep
 * {@code ${pcpanel.root}/logs/logging.log} just to read the exception behind an HTTP 500.
 *
 * <p>Gated with the rest of the dev MCP tools; only present when {@code pcpanel.mcp.dev=true}.
 */
@ApplicationScoped
@IfBuildProperty(name = McpDevTool.FLAG, stringValue = "true")
public class McpLogBuffer {
    private static final int CAPACITY = 250;

    private final Deque<Entry> entries = new ArrayDeque<>(CAPACITY);
    private final BufferHandler handler = new BufferHandler();

    void onStart(@Observes StartupEvent ev) {
        // This app logs through jboss-logmanager (the Quarkus backend), NOT the vanilla JUL root - a
        // handler on java.util.logging.Logger.getLogger("") would never see the records. Attach to the
        // jboss LogContext root instead.
        var root = LogContext.getLogContext().getLogger("");
        for (var h : root.getHandlers()) {
            if (h instanceof BufferHandler) {
                return; // already registered (dev live-reload)
            }
        }
        handler.setLevel(Level.WARNING);
        root.addHandler(handler);
    }

    @PreDestroy
    void stop() {
        LogContext.getLogContext().getLogger("").removeHandler(handler);
    }

    private void record(LogRecord r) {
        var entry = new Entry(
                Instant.ofEpochMilli(r.getMillis()).toString(),
                String.valueOf(r.getLevel()),
                r.getLoggerName(),
                safeMessage(r),
                stackTrace(r.getThrown()));
        synchronized (entries) {
            if (entries.size() >= CAPACITY) {
                entries.removeFirst();
            }
            entries.addLast(entry);
        }
    }

    /** Most recent entry at ERROR/SEVERE level or carrying a throwable, if any. */
    public Entry lastError() {
        synchronized (entries) {
            var it = entries.descendingIterator();
            while (it.hasNext()) {
                var e = it.next();
                if ("SEVERE".equals(e.level()) || "ERROR".equals(e.level()) || e.stackTrace() != null) {
                    return e;
                }
            }
            return null;
        }
    }

    /** Snapshot of the buffered warn/error entries, oldest first, capped to {@code limit}. */
    public List<Entry> recent(int limit) {
        synchronized (entries) {
            var all = new ArrayList<>(entries);
            var from = Math.max(0, all.size() - limit);
            return new ArrayList<>(all.subList(from, all.size()));
        }
    }

    private static String safeMessage(LogRecord r) {
        try {
            var msg = r.getMessage();
            var params = r.getParameters();
            if (msg != null && params != null && params.length > 0 && msg.contains("{0}")) {
                return java.text.MessageFormat.format(msg, params);
            }
            return msg;
        } catch (RuntimeException e) {
            return r.getMessage();
        }
    }

    private static String stackTrace(Throwable t) {
        if (t == null) {
            return null;
        }
        var sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

    private final class BufferHandler extends Handler {
        @Override public void publish(LogRecord r) {
            if (r != null && r.getLevel().intValue() >= Level.WARNING.intValue()) {
                record(r);
            }
        }

        @Override public void flush() {
        }

        @Override public void close() {
        }
    }

    public record Entry(String timestamp, String level, String logger, String message, String stackTrace) {
    }
}
