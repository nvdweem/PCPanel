package com.getpcpanel.sleepdetection;

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import lombok.extern.log4j.Log4j2;

/**
 * Delivers the macOS sleep and wake events of both sources, IOKit and the {@link SuspendResumeWatchdog}, on one
 * ordered thread, and decides there which of them reach the listener. Each {@link SystemEventType#goingToSuspend}
 * starts a new sleep generation and is always delivered; the first {@link SystemEventType#resumedFromSuspend} of a
 * generation is delivered, whichever source reports it, and later ones are dropped. Without a preceding suspend
 * (IOKit inactive or missed) a watchdog resume passes. The decision state is only touched on the event thread.
 */
@Log4j2
final class MacSleepEvents implements AutoCloseable {
    private static final Duration CLOSE_WAIT = Duration.ofSeconds(2);

    private final Consumer<SystemEventType> sink;
    private final ExecutorService executor;
    private final Duration closeWait;
    private volatile boolean closed;
    // Event thread only.
    private boolean suspendSeen;
    private boolean resumeDelivered;

    MacSleepEvents(Consumer<SystemEventType> sink) {
        this(sink, Executors.newSingleThreadExecutor(r -> {
            var t = new Thread(r, "mac-sleep-events");
            t.setDaemon(true);
            return t;
        }), CLOSE_WAIT);
    }

    MacSleepEvents(Consumer<SystemEventType> sink, ExecutorService executor, Duration closeWait) {
        this.sink = sink;
        this.executor = executor;
        this.closeWait = closeWait;
    }

    void fromIoKit(SystemEventType type) {
        submit(() -> {
            switch (type) {
                case goingToSuspend -> {
                    suspendSeen = true;
                    resumeDelivered = false;
                    deliver(type);
                }
                case resumedFromSuspend -> {
                    if (!resumeDelivered) {
                        resumeDelivered = true;
                        deliver(type);
                    }
                }
                default -> deliver(type);
            }
        });
    }

    void fromWatchdog(SystemEventType type) {
        submit(() -> {
            if (type != SystemEventType.resumedFromSuspend) {
                deliver(type);
            } else if (!resumeDelivered) {
                // Only a resume that answers a suspend closes its generation; a lone one leaves the next free to pass.
                resumeDelivered = suspendSeen;
                deliver(type);
            }
        });
    }

    /** IOKit stopped reporting: the watchdog is the only source again, so nothing it reports is a duplicate. */
    void ioKitStopped() {
        submit(() -> {
            suspendSeen = false;
            resumeDelivered = false;
        });
    }

    /** Returns once everything submitted before has been handled. */
    void flush() throws InterruptedException, ExecutionException {
        executor.submit(() -> { }).get();
    }

    private void submit(Runnable task) {
        if (closed) {
            return;
        }
        try {
            executor.execute(() -> {
                if (!closed) {
                    task.run();
                }
            });
        } catch (RejectedExecutionException e) {
            log.debug("macOS sleep event dropped after shutdown");
        }
    }

    private void deliver(SystemEventType type) {
        try {
            sink.accept(type);
        } catch (RuntimeException e) {
            log.warn("Handling macOS sleep event {} failed", type, e);
        }
    }

    @Override
    public void close() {
        closed = true;
        executor.shutdown();
        try {
            if (!executor.awaitTermination(closeWait.toMillis(), TimeUnit.MILLISECONDS)) {
                log.warn("macOS sleep events did not finish within {}", closeWait);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            executor.shutdownNow();
        }
    }
}
