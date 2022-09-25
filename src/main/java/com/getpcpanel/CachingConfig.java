package com.getpcpanel;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
@EnableCaching
public class CachingConfig {
    @Bean
    public CacheManager cacheManager() {
        return new ConcurrentMapCacheManager("icon", "command-icon");
    }

    @CacheEvict(allEntries = true, cacheNames = "icon")
    @Scheduled(fixedDelay = 300_000)
    public void iconEvict() {
    }

    @CacheEvict(allEntries = true, cacheNames = "command-icon")
    @Scheduled(fixedDelay = 1_000)
    public void commandIconEvict() {
    }
}
