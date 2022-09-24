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
    public CacheManager commandImageCacheManager() {
        return new ConcurrentMapCacheManager("command-image");
    }

    @CacheEvict(allEntries = true, cacheNames = "command-image")
    @Scheduled(fixedDelay = 1_000)
    public void cacheEvict() {
    }
}
