package com.getpcpanel;

import io.quarkus.cache.CacheInvalidateAll;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class CachingConfig {

    @CacheInvalidateAll(cacheName = "icon")
    @Scheduled(every = "300s")
    public void iconEvict() {
    }

    @CacheInvalidateAll(cacheName = "command-icon")
    @Scheduled(every = "1s")
    public void commandIconEvict() {
    }
}
