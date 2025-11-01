package com.io.shortly.redirect.webflux.infrastructure.cache;

import java.time.Duration;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum CacheLayer {

    L1(
        "caffeine",
        "shorturl:l1:",
        Duration.ofMinutes(10),
        "redirectCache-L1"
    ),

    L2(
        "redis",
        "shorturl:l2:",
        Duration.ofHours(24),
        "redirectCache-L2"
    );

    private final String name;
    private final String keyPrefix;
    private final Duration ttl;
    private final String metricName;

    public String buildKey(String shortCode) {
        return keyPrefix + shortCode;
    }
}
