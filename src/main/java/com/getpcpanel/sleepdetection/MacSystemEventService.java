package com.getpcpanel.sleepdetection;

import com.getpcpanel.platform.MacBuild;

import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import lombok.extern.log4j.Log4j2;

/**
 * macOS sleep detection. IOKit system power notifications ({@link MacPowerNotifications}) report every sleep and
 * wake, however short, as {@link SystemEventType#goingToSuspend} and {@link SystemEventType#resumedFromSuspend}.
 * The clock-based {@link SuspendResumeWatchdog} runs alongside as a fallback that sees only longer sleeps; a wake
 * both report reaches the listeners once ({@link MacSleepEvents}). If IOKit registration fails the watchdog
 * stands alone. There is no native lock/unlock source here.
 */
@Log4j2
@Startup
@ApplicationScoped
@MacBuild
public class MacSystemEventService {
    @Inject
    Event<Object> eventBus;

    private final MacSleepEvents events = new MacSleepEvents(this::fire);
    private final SuspendResumeWatchdog watchdog = new SuspendResumeWatchdog(events::fromWatchdog);
    private MacPowerNotifications power;

    @PostConstruct
    public void init() {
        try {
            var notifications = new MacPowerNotifications(events::fromIoKit, events::ioKitStopped);
            if (notifications.start()) {
                power = notifications;
            } else {
                notifications.close();
            }
        } catch (Throwable t) {
            log.warn("macOS power notifications unavailable", t);
        }
        watchdog.start();
        log.info(power != null
                ? "macOS sleep detection started (IOKit power notifications, clock watchdog as fallback)"
                : "macOS sleep detection started (clock watchdog only: resume from longer sleeps)");
    }

    @PreDestroy
    public void shutdown() {
        watchdog.stop();
        if (power != null) {
            power.close();
        }
        events.close();
    }

    private void fire(SystemEventType type) {
        log.info("macOS system event: {}", type);
        eventBus.fire(new SystemEvent(type));
    }
}
