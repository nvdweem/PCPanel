package com.getpcpanel.util.app;

import java.util.concurrent.atomic.AtomicBoolean;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;

@ApplicationScoped
public class AppShutdownState {
    private static final AtomicBoolean SHUTTING_DOWN = new AtomicBoolean(false);

    public static boolean isShuttingDown() {
        return SHUTTING_DOWN.get();
    }

    void onStart(@Observes StartupEvent event) {
        SHUTTING_DOWN.set(false);
    }

    void onShutdown(@Observes ShutdownEvent event) {
        SHUTTING_DOWN.set(true);
    }

    @PostConstruct
    void registerJvmShutdownHook() {
        // Deliberately a raw JVM hook in addition to the ShutdownEvent observer: JVM shutdown hooks run
        // concurrently, so readers on other threads (WebSocket send paths) need this flag set as soon as
        // any exit path starts — including exits where Quarkus's own shutdown sequence is delayed or
        // never reaches the CDI observer. It only flips an AtomicBoolean, so it is safe at any point.
        Runtime.getRuntime().addShutdownHook(new Thread(() -> SHUTTING_DOWN.set(true), "AppShutdownState shutdown hook"));
    }
}

