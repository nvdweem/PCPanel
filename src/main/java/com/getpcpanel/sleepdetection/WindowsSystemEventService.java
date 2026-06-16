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
 * Windows sleep/session detection, callback-free so it works in the GraalVM native image (a JNA
 * window-procedure callback segfaults there). Two downcall-only sources:
 *
 * <ul>
 *   <li>resume-from-suspend via the cross-platform {@link SuspendResumeWatchdog};</li>
 *   <li>lock/unlock by polling {@link Win32Desktop#OpenInputDesktop}, which fails while the secure
 *       lock-screen desktop is active.</li>
 * </ul>
 *
 * <p>There is intentionally no {@link SystemEventType#goingToSuspend}: without a window-proc callback
 * Windows gives no advance suspend notice, but the watchdog restores lighting on the subsequent wake.
 */
@Log4j2
@Startup
@ApplicationScoped
@WindowsBuild
public class WindowsSystemEventService {
    private static final long LOCK_POLL_INTERVAL_MS = 1_000L;

    @Inject
    Event<Object> eventBus;

    private final SuspendResumeWatchdog watchdog = new SuspendResumeWatchdog(this::fire);
    private volatile boolean running;
    private Thread lockPoller;

    @PostConstruct
    public void init() {
        running = true;
        watchdog.start();
        lockPoller = new Thread(this::pollLockState, "windows-lock-poller");
        lockPoller.setDaemon(true);
        lockPoller.start();
        log.info("Windows sleep/session detection started");
    }

    @PreDestroy
    public void shutdown() {
        running = false;
        watchdog.stop();
        if (lockPoller != null) {
            lockPoller.interrupt();
        }
    }

    private void pollLockState() {
        var wasLocked = false;
        while (running) {
            try {
                Thread.sleep(LOCK_POLL_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            boolean locked;
            try {
                locked = isWorkstationLocked();
            } catch (Throwable e) { // NOSONAR - any JNA/linkage failure must not kill the poller
                log.debug("Lock-state poll failed; assuming unlocked", e);
                locked = false;
            }
            if (locked != wasLocked) {
                wasLocked = locked;
                fire(locked ? SystemEventType.locked : SystemEventType.unlocked);
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

    private void fire(SystemEventType type) {
        log.debug("Windows system event: {}", type);
        eventBus.fire(new SystemEvent(type));
    }
}
