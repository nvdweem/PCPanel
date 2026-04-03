package com.getpcpanel;

import io.quarkus.cache.CacheInvalidateAll;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.jbosslog.JBossLog;

@JBossLog
@ApplicationScoped
public class CachingConfig {

    @CacheInvalidateAll(cacheName = "icon")
    @Scheduled(every = "300s")
    public void iconEvict() {
        log.debug("Evicting icon cache");
    }

    @CacheInvalidateAll(cacheName = "command-icon")
    @Scheduled(every = "1s")
    public void commandIconEvict() {
    }
}


import io.quarkus.cache.CacheInvalidateAll;
import jakarta.enterprise.inject.Produces;
import io.quarkus.scheduler.Scheduled;

public class CachingConfig {
    @Produces
    public CacheManager cacheManager() {
        return new ConcurrentMapCacheManager("icon", "command-icon");
    }

    @CacheInvalidateAll(cacheName="icon")
    @Scheduled(every="300s")
    public void iconEvict() {
    }

    @CacheInvalidateAll(cacheName="command-icon")
    @Scheduled(every="1s")
    public void commandIconEvict() {
    }
}
