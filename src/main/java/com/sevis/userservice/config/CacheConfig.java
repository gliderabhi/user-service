package com.sevis.userservice.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.concurrent.TimeUnit;

// In-process (Caffeine) caching — this service runs as a single instance with
// no shared/replicated state, so there's no need for a centralized cache here;
// each cache below is evicted explicitly by the write paths that invalidate it
// (see @CacheEvict usages), with a short TTL as a backstop only.
//
// Only pure profile/lookup reads are cached here (GET by id, GET all). Auth
// flows (login, google login/complete, logout, signup) and role-resolution
// checks are never cached — they either mutate state (sessions) or are
// security decisions that must always see fresh data.
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        SimpleCacheManager manager = new SimpleCacheManager();
        manager.setCaches(List.of(
                buildCache("userById", 5, TimeUnit.MINUTES, 1000),
                buildCache("allUsers", 30, TimeUnit.SECONDS, 10)
        ));
        return manager;
    }

    private CaffeineCache buildCache(String name, long ttl, TimeUnit unit, int maxSize) {
        return new CaffeineCache(name, Caffeine.newBuilder()
                .expireAfterWrite(ttl, unit)
                .maximumSize(maxSize)
                .recordStats()
                .build());
    }
}
