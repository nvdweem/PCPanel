package com.getpcpanel.sleepdetection;

import com.getpcpanel.platform.WindowsBuild;

import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import lombok.extern.log4j.Log4j2;

/**
 * Windows sleep/session detection. Three sources:
 *
 * <ul>
 *   <li>resume-from-suspend via the cross-platform {@link SuspendResumeWatchdog} (downcall-only);</li>
 *   <li>lock/unlock, display power off/on and advance {@link SystemEventType#goingToSuspend} notice
 *       via {@link WindowsPowerEventMonitor}, the one window that listens for the session/power
 *       messages. Lock/unlock comes from {@code WTSRegisterSessionNotification} — the OS states the
 *       transition rather than us inferring it;</li>
 *   <li>lock/unlock <em>fallback</em> by polling {@link Win32Desktop#OpenInputDesktop}, which fails
 *       while the secure lock-screen desktop is active (downcall-only). Only used while the window
 *       above has not (yet) registered for session notifications — at boot that registration fails
 *       until the Remote Desktop Services service is up, so the poller keeps re-trying it and stands
 *       down as soon as it succeeds. Samples are debounced through {@link LockStateTracker}:
 *       {@code OpenInputDesktop} also fails right after logon, during a desktop switch and under the
 *       UAC secure desktop, and treating that as a lock blacked out the panels until the user touched
 *       a lighting setting (#145).</li>
 * </ul>
 *
 * <p>The power-event source uses a window-procedure {@link com.sun.jna.Callback}; that once segfaulted
 * the native image because of the non-headless AWT toolkit, which the Windows build no longer
 * initialises ({@code -Djava.awt.headless=true}). Resume is left to the watchdog, so
 * {@link WindowsPowerEventMonitor} only needs the suspend half of the suspend/resume notification.
 */
@Log4j2
@Startup
@ApplicationScoped
@WindowsBuild
public class WindowsSystemEventService {
    private static final long LOCK_POLL_INTERVAL_MS = 1_000L;
    /** While falling back, re-try the WTS session-notification registration once per this many poll ticks. */
    private static final int SESSION_REGISTRATION_RETRY_TICKS = 5;

    @Inject
    Event<Object> eventBus;

    private final SuspendResumeWatchdog watchdog = new SuspendResumeWatchdog(this::fire);
    private final WindowsPowerEventMonitor powerEventMonitor = new WindowsPowerEventMonitor(this::fire);
    private volatile boolean running;
    private Thread lockPoller;

    @PostConstruct
    public void init() {
        running = true;
        watchdog.start();
        powerEventMonitor.start();
        lockPoller = new Thread(this::pollLockState, "windows-lock-poller");
        lockPoller.setDaemon(true);
        lockPoller.start();
        log.info("Windows sleep/session detection started");
    }

    @PreDestroy
    public void shutdown() {
        running = false;
        watchdog.stop();
        powerEventMonitor.stop();
        if (lockPoller != null) {
            lockPoller.interrupt();
        }
    }

    private void pollLockState() {
        var tracker = new LockStateTracker();
        var ticksUntilRegistrationRetry = 0;
        while (running) {
            try {
                Thread.sleep(LOCK_POLL_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            // The window monitor's WTS session events are authoritative; while they are live this
            // guess-based detector must stay silent (its false positives blacked out the panels, #145).
            // The registration is re-tried from here because at boot it fails until the Remote Desktop
            // Services service is up — exactly the window in which an auto-started app runs (#145).
            if (!powerEventMonitor.hasAuthoritativeLockEvents() && --ticksUntilRegistrationRetry <= 0) {
                ticksUntilRegistrationRetry = SESSION_REGISTRATION_RETRY_TICKS;
                if (powerEventMonitor.tryRegisterSessionNotifications()) {
                    log.info("WTS session notifications registered; lock polling stands down");
                }
            }
            if (powerEventMonitor.hasAuthoritativeLockEvents()) {
                // WTS only reports transitions, so nothing authoritative will ever clear a lock this
                // fallback asserted — clear it ourselves or the panels stay dark forever.
                if (tracker.confirmedLocked()) {
                    fire(SystemEventType.unlocked);
                    tracker = new LockStateTracker();
                }
                continue;
            }
            boolean locked;
            try {
                locked = isWorkstationLocked();
            } catch (Throwable e) { // NOSONAR - any JNA/linkage failure must not kill the poller
                log.debug("Lock-state poll failed; assuming unlocked", e);
                locked = false;
            }
            var event = tracker.sample(locked);
            if (event != null) {
                fire(event);
            }
        }
    }

    /**
     * The input desktop can't be opened from a normal process while the workstation is locked (the
     * secure Winlogon desktop owns input), so a {@code null} handle means locked.
     */
    private boolean isWorkstationLocked() {
        var hDesktop = Win32Desktop.INSTANCE.OpenInputDesktop(0, false, Win32Desktop.DESKTOP_SWITCHDESKTOP);
        if (hDesktop == null) {
            return true;
        }
        Win32Desktop.INSTANCE.CloseDesktop(hDesktop);
        return false;
    }

    /**
     * Observers run synchronously on the caller's thread — the lock poller, or the native window
     * procedure of {@link WindowsPowerEventMonitor}. A throwing observer must not kill the poller
     * (that would end lock detection for the session) or unwind into the native message pump.
     */
    private void fire(SystemEventType type) {
        // Info, not debug: these are rare, and #145-class reports live or die on whether a user's log
        // shows which event darkened the panels.
        log.info("Windows system event: {}", type);
        try {
            eventBus.fire(new SystemEvent(type));
        } catch (Throwable e) { // NOSONAR - see javadoc: this thread must survive any observer failure
            log.warn("Handler for system event {} failed", type, e);
        }
    }
}
