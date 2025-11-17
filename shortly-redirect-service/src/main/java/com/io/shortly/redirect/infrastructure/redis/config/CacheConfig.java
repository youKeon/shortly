package com.io.shortly.redirect.infrastructure.redis.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.io.shortly.redirect.domain.Redirect;
import java.time.Duration;
import java.util.List;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableCaching
public class CacheConfig {

    private static final int MAX_CACHE_SIZE = 100000;  // 10만
    private static final Duration CACHE_TTL = Duration.ofMinutes(10);
    public static final String REDIRECT_CACHE_NAME = "redirects";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCaffeine(caffeineConfig());
        cacheManager.setCacheNames(List.of(REDIRECT_CACHE_NAME));
        return cacheManager;
    }

    @Bean
    public Caffeine<Object, Object> caffeineConfig() {
        return Caffeine.newBuilder()
            .maximumSize(MAX_CACHE_SIZE)
            .expireAfterWrite(CACHE_TTL)
            .recordStats();  // 메트릭 수집 활성화
    }

    @Bean
    public Cache<String, Redirect> caffeineCache(CacheManager cacheManager) {
        var cache = cacheManager.getCache(REDIRECT_CACHE_NAME);
        if (cache instanceof CaffeineCache) {
            Cache rawCache = ((CaffeineCache) cache).getNativeCache();
            return (Cache<String, Redirect>) rawCache;
        }
        throw new IllegalStateException("CaffeineCache not found");
    }
}
