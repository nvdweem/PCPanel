package com.getpcpanel.util;

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
        Runtime.getRuntime().addShutdownHook(new Thread(() -> SHUTTING_DOWN.set(true), "AppShutdownState shutdown hook"));
    }
}

