package com.io.shortly.redirect.infrastructure.cache.caffeine;

import com.github.benmanes.caffeine.cache.Cache;
import com.io.shortly.redirect.domain.Redirect;
import com.io.shortly.redirect.domain.RedirectCache;
import com.io.shortly.redirect.infrastructure.cache.CacheLayer;
import com.io.shortly.redirect.infrastructure.metrics.CacheMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Slf4j
@Primary
@Component
public class RedirectCacheCaffeineImpl implements RedirectCache {

    private static final CacheLayer LAYER = CacheLayer.L1;

    private final Cache<String, Redirect> caffeineCache;
    private final RedirectCache redisCache;
    private final CacheMetrics l1Metrics;

    public RedirectCacheCaffeineImpl(
        Cache<String, Redirect> caffeineCache,
        @Qualifier("redisCache") RedirectCache redisCache,
        MeterRegistry meterRegistry
    ) {
        this.caffeineCache = caffeineCache;
        this.redisCache = redisCache;
        this.l1Metrics = CacheMetrics.of(meterRegistry, LAYER.getMetricName(), "L1");
    }

    @Override
    public Mono<Void> put(Redirect redirect) {
        // Write-through: L1 + L2
        String key = LAYER.buildKey(redirect.getShortCode());
        caffeineCache.put(key, redirect);
        l1Metrics.recordPut();
        log.debug("[Cache:L1] Put: shortCode={}", redirect.getShortCode());

        return redisCache.put(redirect)
            .onErrorResume(error -> {
                log.warn("[Cache:L2] Put failed: shortCode={}, continuing", redirect.getShortCode());
                return Mono.empty();
            });
    }

    @Override
    public Mono<Redirect> get(String shortCode) {
        // Check L1 first
        String key = LAYER.buildKey(shortCode);
        Redirect l1Result = caffeineCache.getIfPresent(key);

        if (l1Result != null) {
            l1Metrics.recordHit();
            log.debug("[Cache:L1] Hit: shortCode={}", shortCode);
            return Mono.just(l1Result);
        }

        l1Metrics.recordMiss();
        log.debug("[Cache:L1] Miss: shortCode={}", shortCode);

        // L1 miss → Check L2
        return redisCache.get(shortCode)
            .doOnNext(redirect -> {
                // Backfill L1 cache on L2 hit
                caffeineCache.put(key, redirect);
                l1Metrics.recordPut();
                log.debug("[Cache:L1] Backfilled from L2: shortCode={}", shortCode);
            })
            .onErrorResume(error -> {
                log.warn("[Cache:L2] Read failed: shortCode={}, continuing", shortCode);
                return Mono.empty();
            });
    }

    @Override
    public Mono<Void> evict(String shortCode) {
        // Evict from both L1 and L2
        String key = LAYER.buildKey(shortCode);
        caffeineCache.invalidate(key);
        l1Metrics.recordEvict();
        log.debug("[Cache:L1] Evicted: shortCode={}", shortCode);

        return redisCache.evict(shortCode)
            .onErrorResume(error -> {
                log.warn("[Cache:L2] Eviction failed: shortCode={}, continuing", shortCode);
                return Mono.empty();
            });
    }
}
