package com.io.shortly.redirect.infrastructure.redis.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.io.shortly.redirect.domain.Redirect;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CacheConfig {

    private final long l1MaxSize;
    private final Duration l1Ttl;

    public CacheConfig(
        @Value("${shortly.cache.l1.max-size:100000}") long l1MaxSize,
        @Value("${shortly.cache.l1.ttl:10m}") Duration l1Ttl
    ) {
        this.l1MaxSize = l1MaxSize;
        this.l1Ttl = l1Ttl;
    }

    @Bean
    public Cache<String, Redirect> caffeineCache() {
        return Caffeine.newBuilder()
            .maximumSize(l1MaxSize)
            .expireAfterWrite(l1Ttl)
            .recordStats()
            .build();
    }
}
