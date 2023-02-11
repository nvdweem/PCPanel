package com.getpcpanel.util;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;

/**
 * <a href="https://stackoverflow.com/a/38296055">From stackoverflow</a>
 */
@Service
public class Debouncer {
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final Map<Object, Future<?>> delayedMap = new ConcurrentHashMap<>();

    /**
     * Debounces {@code callable} by {@code delay}, i.e., schedules it to be executed after {@code delay},
     * or cancels its execution if the method is called with the same key within the {@code delay} again.
     */
    public void debounce(Object key, Runnable runnable, long delay, TimeUnit unit) {
        var prev = delayedMap.put(key, scheduler.schedule(() -> {
            try {
                runnable.run();
            } finally {
                delayedMap.remove(key);
            }
        }, delay, unit));
        if (prev != null) {
            prev.cancel(true);
        }
    }

    public void prevent(Object key) {
        var prev = delayedMap.remove(key);
        if (prev != null) {
            prev.cancel(true);
        }
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdownNow();
    }
}
