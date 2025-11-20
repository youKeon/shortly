package com.io.shortly.redirect.infrastructure.metrics;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import com.io.shortly.redirect.domain.Redirect;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CacheMetrics {

    private final Cache<String, Redirect> caffeineCache;
    private final MeterRegistry meterRegistry;

    private static final Tags L1_TAGS = Tags.of(Tag.of("cache", "L1"));

    @PostConstruct
    public void registerCacheMetrics() {
        // 캐시 크기
        Gauge.builder("cache.size", caffeineCache, Cache::estimatedSize)
            .tags(L1_TAGS)
            .description("현재 L1 캐시 엔트리 수")
            .register(meterRegistry);

        // 히트 횟수
        Gauge.builder("cache.hit.count", caffeineCache, cache -> cache.stats().hitCount())
            .tags(L1_TAGS)
            .description("L1 캐시 히트 총 횟수")
            .register(meterRegistry);

        // 미스 횟수
        Gauge.builder("cache.miss.count", caffeineCache, cache -> cache.stats().missCount())
            .tags(L1_TAGS)
            .description("L1 캐시 미스 총 횟수")
            .register(meterRegistry);

        // 축출 횟수
        Gauge.builder("cache.eviction.count", caffeineCache, cache -> cache.stats().evictionCount())
            .tags(L1_TAGS)
            .description("L1 캐시 축출 총 횟수 (TTL 만료 + 용량 초과)")
            .register(meterRegistry);

        // 히트율
        Gauge.builder("cache.hit.ratio", caffeineCache, cache -> {
                CacheStats stats = cache.stats();
                long total = stats.hitCount() + stats.missCount();
                if (total == 0) {
                    return 0.0;
                }
                return (double) stats.hitCount() / total;
            })
            .tags(L1_TAGS)
            .description("L1 캐시 히트율 (0.0 ~ 1.0)")
            .register(meterRegistry);

        log.info("[Metrics] Caffeine L1 캐시 메트릭 등록 완료");
    }
}
