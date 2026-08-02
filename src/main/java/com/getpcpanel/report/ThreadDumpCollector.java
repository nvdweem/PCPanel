package com.getpcpanel.report;

import java.lang.management.ManagementFactory;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.log4j.Log4j2;

/**
 * A stack for every live thread, as the bundle's answer to "the application is running but does
 * nothing". Commands execute one at a time on a single thread, so one that never returns takes every
 * dial and button with it while the UI and the overlay carry on drawing — a state that looks identical
 * to dead hardware from the outside and that no reporter can describe beyond "it stopped working".
 * The stack of the thread that is stuck names the culprit outright.
 *
 * <p>The stacks come from {@link Thread#getAllStackTraces()} rather than
 * {@link java.lang.management.ThreadMXBean#findDeadlockedThreads()}, which would look like the more
 * direct tool and is not: it reports cycles between Java monitors only. A thread parked in a native
 * call while holding a Java monitor is not blocked <em>on</em> a monitor and closes no cycle it can
 * see, so the deadlocks that involve a native lock — the ones that have actually cost this project
 * releases — are exactly the ones it stays silent about. A plain dump shows both halves.
 *
 * <p>Monitor ownership is asked for separately and only enriches the dump: it needs
 * {@code java.lang.management}, whose support in a native image is narrower than the plain thread API.
 * Its absence costs the owner's name, not the dump.
 */
@Log4j2
@ApplicationScoped
public class ThreadDumpCollector {
    /** Frames kept per thread. Deep enough for any stack worth reading, bounded so one runaway cannot fill the bundle. */
    static final int MAX_FRAMES = 60;
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public String collect() {
        var stacks = Thread.getAllStackTraces();
        var locks = lockOwners();
        var out = new StringBuilder();

        out.append("PCPanel — thread dump\n\n");
        append(out, "taken at", LocalDateTime.now().format(STAMP));
        append(out, "threads", String.valueOf(stacks.size()));
        append(out, "lock details", locks == null ? "unavailable on this runtime" : "available");
        out.append('\n');

        stacks.entrySet().stream()
              .sorted(Comparator.comparing(e -> e.getKey().getName(), String.CASE_INSENSITIVE_ORDER))
              .forEach(entry -> appendThread(out, entry.getKey(), entry.getValue(), locks));

        return out.toString();
    }

    private static void appendThread(StringBuilder out, Thread thread, StackTraceElement[] stack, @Nullable Map<Long, LockInfo> locks) {
        out.append('"').append(thread.getName()).append('"')
           .append(" id=").append(thread.threadId())
           .append(' ').append(thread.getState());
        if (thread.isDaemon()) {
            out.append(" daemon");
        }
        out.append('\n');

        var lock = locks == null ? null : locks.get(thread.threadId());
        if (lock != null) {
            out.append('\t').append(lock.describe(thread.getState())).append('\n');
        }

        var shown = Math.min(stack.length, MAX_FRAMES);
        for (var i = 0; i < shown; i++) {
            out.append("\tat ").append(stack[i]).append('\n');
        }
        if (stack.length > shown) {
            out.append("\t... ").append(stack.length - shown).append(" more frames\n");
        }
        out.append('\n');
    }

    /**
     * The monitor a thread is contending for or waiting on, and who owns it.
     *
     * <p>The two are not the same thing and must not read as if they were. A {@code BLOCKED} thread
     * wants a monitor someone else holds — that is contention, and the owner is the other half of any
     * deadlock. A thread inside {@link Object#wait()} reports the very monitor it <em>released</em> on
     * the way in and has no owner, which is the ordinary resting state of most idle pools. Rendering
     * both as "waiting to lock" would bury the one line that matters under a dozen that never did.
     */
    private record LockInfo(String lockName, @Nullable String ownerName, long ownerId) {
        String describe(Thread.State state) {
            var verb = state == Thread.State.BLOCKED ? "- waiting to lock <" : "- waiting on <";
            var held = ownerName == null ? "" : " held by \"" + ownerName + "\" (id=" + ownerId + ')';
            return verb + lockName + '>' + held;
        }
    }

    /**
     * What each thread is contending for or waiting on, keyed by thread id, or {@code null} when the
     * runtime does not offer it. Stacks are not requested here — they are already in hand.
     */
    @Nullable
    private static Map<Long, LockInfo> lockOwners() {
        try {
            var bean = ManagementFactory.getThreadMXBean();
            var infos = bean.getThreadInfo(bean.getAllThreadIds(), 0);
            var result = new HashMap<Long, LockInfo>();
            for (var info : infos) {
                if (info == null || StringUtils.isBlank(info.getLockName())) {
                    continue;
                }
                result.put(info.getThreadId(), new LockInfo(info.getLockName(), info.getLockOwnerName(), info.getLockOwnerId()));
            }
            return result;
        } catch (Throwable e) {
            log.debug("Monitor ownership is not available; the dump keeps its stacks", e);
            return null;
        }
    }

    private static void append(StringBuilder out, String label, String value) {
        out.append("  ").append(StringUtils.rightPad(label + ':', 16)).append(value).append('\n');
    }
}
