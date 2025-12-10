package com.io.shortly.redirect.infrastructure.cache.caffeine;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.io.shortly.redirect.domain.Redirect;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CaffeineConfig {

    private final long l1MaxSize;

    public CaffeineConfig(
            @Value("${shortly.cache.l1.max-size:100000}") long l1MaxSize
    ) {
        this.l1MaxSize = l1MaxSize;
    }

    @Bean
    public Cache<String, Redirect> caffeineCache() {
        return Caffeine.newBuilder()
                .maximumSize(l1MaxSize)
                .recordStats()
                .build();
    }
}
