package com.getpcpanel.sleepdetection;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;

import lombok.extern.log4j.Log4j2;

/**
 * IOKit system power notifications ({@code IORegisterForSystemPower}), delivered on the CFRunLoop of a dedicated
 * daemon thread. Reports every sleep and wake, however short, through {@link MacPowerMessages}.
 *
 * <p>The worker thread owns the registration from start to teardown. {@link #close()} (or a start that gave up
 * waiting) only records the cancellation and asks the loop to stop; the worker then tears down whatever it holds,
 * however late it gets there.
 */
@Log4j2
final class MacPowerNotifications implements AutoCloseable {
    private static final Duration START_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration CLOSE_TIMEOUT = Duration.ofSeconds(5);

    /** The native side, so the lifecycle can be exercised without IOKit. */
    interface Backend {
        /** Registers and attaches to the calling thread's run loop; false (already logged) on failure. */
        boolean register(MacPowerMessages messages);

        /** Runs the loop until {@link #stopLoop()} was called, also when it was called beforehand. */
        void runLoop();

        /** Thread-safe, idempotent. */
        void stopLoop();

        /** Releases everything {@link #register} acquired, whatever stage it reached. */
        void teardown();

        void allowPowerChange(long notificationId);
    }

    private final Backend backend;
    private final Duration startTimeout;
    private final Runnable onStopped;
    private final MacPowerMessages messages;
    private final CountDownLatch started = new CountDownLatch(1);
    private final CountDownLatch finished = new CountDownLatch(1);
    private volatile boolean cancelled;
    private volatile boolean registered;
    private Thread thread;

    /**
     * @param onEvent   called on IOKit's callback thread, so it must only hand the event over
     * @param onStopped called when the registration ends without {@link #close()} having asked for it
     */
    MacPowerNotifications(Consumer<SystemEventType> onEvent, Runnable onStopped) {
        this(new IoKitBackend(), START_TIMEOUT, onEvent, onStopped);
    }

    MacPowerNotifications(Backend backend, Duration startTimeout, Consumer<SystemEventType> onEvent, Runnable onStopped) {
        this.backend = backend;
        this.startTimeout = startTimeout;
        this.onStopped = onStopped;
        messages = new MacPowerMessages(onEvent, backend::allowPowerChange, Runnable::run);
    }

    /** Registers and starts the run loop thread; false (already logged) when registration failed or took too long. */
    boolean start() {
        thread = new Thread(this::run, "mac-power-notifications");
        thread.setDaemon(true);
        thread.start();
        try {
            if (!started.await(startTimeout.toMillis(), TimeUnit.MILLISECONDS)) {
                log.warn("macOS power notifications did not start within {}", startTimeout);
                cancel();
                return false;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            cancel();
            return false;
        }
        return registered;
    }

    private void run() {
        try {
            if (!backend.register(messages)) {
                log.warn("Registering for macOS power notifications failed; relying on the clock watchdog for wake detection");
                return;
            }
            if (cancelled) {
                return;
            }
            registered = true;
            started.countDown();
            backend.runLoop();
        } catch (Throwable t) {
            log.warn("macOS power notifications failed; relying on the clock watchdog for wake detection", t);
        } finally {
            var wasRegistered = registered;
            registered = false;
            try {
                backend.teardown();
            } catch (Throwable t) {
                log.debug("Releasing macOS power notifications failed", t);
            } finally {
                started.countDown();
                finished.countDown();
                if (wasRegistered && !cancelled) {
                    log.warn("macOS power notifications stopped; relying on the clock watchdog for wake detection");
                    onStopped.run();
                }
            }
        }
    }

    private void cancel() {
        cancelled = true;
        backend.stopLoop();
    }

    boolean isRunning() {
        return finished.getCount() > 0 && thread != null;
    }

    @Override
    public void close() {
        if (thread == null) {
            return;
        }
        cancel();
        var interrupted = false;
        var deadline = System.nanoTime() + CLOSE_TIMEOUT.toNanos();
        try {
            while (true) {
                try {
                    if (!finished.await(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS)) {
                        log.warn("macOS power notifications did not shut down within {}", CLOSE_TIMEOUT);
                    }
                    break;
                } catch (InterruptedException e) {
                    interrupted = true;
                }
            }
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /** The IOKit and CoreFoundation calls. */
    private static final class IoKitBackend implements Backend {
        private static final int KCF_RUN_LOOP_SLICE_SECONDS = 1;

        private final Object lock = new Object();
        private final PointerByReference notifyPort = new PointerByReference();
        private final IntByReference notifier = new IntByReference();
        // Held for the life of the registration: IOKit keeps only the native stub.
        private MacPowerLib.IOKit.PowerCallback callback;
        private volatile int rootPort;
        private volatile boolean stopped;
        private Pointer source;
        private Pointer mode;
        /** Retained while set, so a stop never reaches a loop CoreFoundation already released. */
        private Pointer loop;

        @Override
        public boolean register(MacPowerMessages messages) {
            var iokit = MacPowerLib.IOKit.INSTANCE;
            var cf = MacPowerLib.CoreFoundation.INSTANCE;
            try {
                callback = (refcon, service, messageType, messageArgument) -> messages.handle(messageType, Pointer.nativeValue(messageArgument));
                rootPort = iokit.IORegisterForSystemPower(null, notifyPort, callback, notifier);
                if (rootPort == 0 || notifyPort.getValue() == null) {
                    return false;
                }
                source = iokit.IONotificationPortGetRunLoopSource(notifyPort.getValue());
                mode = cf.CFStringCreateWithCString(null, "kCFRunLoopDefaultMode", MacPowerLib.CoreFoundation.kCFStringEncodingUTF8);
                if (source == null || mode == null) {
                    return false;
                }
                synchronized (lock) {
                    loop = cf.CFRunLoopGetCurrent();
                    cf.CFRetain(loop);
                    cf.CFRunLoopAddSource(loop, source, mode);
                }
                return true;
            } catch (Throwable t) {
                log.warn("IOKit power registration failed", t);
                return false;
            }
        }

        @Override
        public void runLoop() {
            var cf = MacPowerLib.CoreFoundation.INSTANCE;
            while (!stopped) {
                cf.CFRunLoopRunInMode(mode, KCF_RUN_LOOP_SLICE_SECONDS, (byte) 0);
            }
        }

        @Override
        public void stopLoop() {
            stopped = true;
            synchronized (lock) {
                if (loop != null) {
                    MacPowerLib.CoreFoundation.INSTANCE.CFRunLoopStop(loop);
                }
            }
        }

        @Override
        public void teardown() {
            var iokit = MacPowerLib.IOKit.INSTANCE;
            var cf = MacPowerLib.CoreFoundation.INSTANCE;
            stopped = true;
            synchronized (lock) {
                if (loop != null) {
                    if (source != null && mode != null) {
                        cf.CFRunLoopRemoveSource(loop, source, mode);
                    }
                    cf.CFRelease(loop);
                    loop = null;
                }
            }
            if (notifier.getValue() != 0) {
                iokit.IODeregisterForSystemPower(notifier);
            }
            var port = rootPort;
            rootPort = 0;
            if (port != 0) {
                iokit.IOServiceClose(port);
            }
            if (notifyPort.getValue() != null) {
                iokit.IONotificationPortDestroy(notifyPort.getValue());
            }
            if (mode != null) {
                cf.CFRelease(mode);
            }
        }

        @Override
        public void allowPowerChange(long notificationId) {
            var port = rootPort;
            if (port != 0) {
                MacPowerLib.IOKit.INSTANCE.IOAllowPowerChange(port, notificationId);
            }
        }
    }
}
