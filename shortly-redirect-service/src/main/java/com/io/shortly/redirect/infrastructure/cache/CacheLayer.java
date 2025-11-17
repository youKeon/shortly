package com.io.shortly.redirect.infrastructure.cache;

import java.time.Duration;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CacheLayer {
    L1("redirect:l1:", Duration.ofMinutes(10), "caffeine"),
    L2("redirect:l2:", Duration.ofMinutes(30), "redis");

    private final String keyPrefix;
    private final Duration ttl;
    private final String metricName;
}
