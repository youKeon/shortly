package com.io.shortly.redirect.webflux.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.io.shortly.redirect.webflux.domain.Redirect;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("webflux")
public class CacheConfig {

    private static final int MAX_CACHE_SIZE = 10_000;
    private static final Duration CACHE_TTL = Duration.ofMinutes(10);

    @Bean
    public Cache<String, Redirect> caffeineCache() {
        return Caffeine.newBuilder()
            .maximumSize(MAX_CACHE_SIZE)
            .expireAfterWrite(CACHE_TTL)
            .recordStats()
            .build();
    }
}
