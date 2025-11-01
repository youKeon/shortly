package com.io.shortly.redirect.webflux.infrastructure.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

public record CacheMetrics(
    Counter hitCounter,
    Counter missCounter,
    Counter putCounter,
    Counter evictCounter
) {

    public static CacheMetrics of(MeterRegistry meterRegistry, String cacheName, String level) {
        String application = "shortly-redirect-service";

        Counter hit = buildCounter(meterRegistry, "cache_gets_total", cacheName, level, "hit",
            "Number of cache hits", application);
        Counter miss = buildCounter(meterRegistry, "cache_gets_total", cacheName, level, "miss",
            "Number of cache misses", application);
        Counter put = buildCounter(meterRegistry, "cache_puts_total", cacheName, level, null,
            "Number of cache puts", application);
        Counter evict = buildCounter(meterRegistry, "cache_evictions_total", cacheName, level, null,
            "Number of cache evictions", application);

        return new CacheMetrics(hit, miss, put, evict);
    }

    public static CacheMetrics of(MeterRegistry meterRegistry, String cacheName) {
        return of(meterRegistry, cacheName, null);
    }

    private static Counter buildCounter(
        MeterRegistry registry,
        String name,
        String cacheName,
        String level,
        String result,
        String description,
        String application
    ) {
        Counter.Builder builder = Counter.builder(name)
            .tag("cache", cacheName)
            .tag("application", application)
            .description(description);

        if (level != null) {
            builder.tag("level", level);
        }

        if (result != null) {
            builder.tag("result", result);
        }

        return builder.register(registry);
    }

    public void recordHit() {
        hitCounter.increment();
    }

    public void recordMiss() {
        missCounter.increment();
    }

    public void recordPut() {
        putCounter.increment();
    }

    public void recordEvict() {
        evictCounter.increment();
    }
}
