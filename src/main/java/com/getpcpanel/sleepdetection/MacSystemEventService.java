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
 * macOS sleep detection (best-effort). The native IOKit/NSWorkspace power notifications require an
 * Objective-C callback + CFRunLoop, and JNA callbacks crash the GraalVM native image, so this relies
 * on the callback-free {@link SuspendResumeWatchdog} to restore lighting on wake. There is no native
 * lock/unlock source here.
 */
@Log4j2
@Startup
@ApplicationScoped
@MacBuild
public class MacSystemEventService {
    @Inject
    Event<Object> eventBus;

    private final SuspendResumeWatchdog watchdog = new SuspendResumeWatchdog(this::fire);

    @PostConstruct
    public void init() {
        watchdog.start();
        log.info("macOS sleep detection started (resume-from-suspend only)");
    }

    @PreDestroy
    public void shutdown() {
        watchdog.stop();
    }

    private void fire(SystemEventType type) {
        log.debug("macOS system event: {}", type);
        eventBus.fire(new SystemEvent(type));
    }
}
