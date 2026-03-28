package com.getpcpanel;

import io.quarkus.cache.CacheInvalidate;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class CachingConfig {
    @Scheduled(delayed = "300s", every = "300s")
    @CacheInvalidate(cacheName = "icon")
    public void iconEvict() {
    }

    @Scheduled(delayed = "1s", every = "1s")
    @CacheInvalidate(cacheName = "icon")
    public void commandIconEvict() {
    }
}
