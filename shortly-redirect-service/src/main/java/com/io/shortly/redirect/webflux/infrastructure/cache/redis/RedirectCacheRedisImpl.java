package com.io.shortly.redirect.webflux.infrastructure.cache.redis;

import static com.io.shortly.redirect.webflux.infrastructure.cache.CacheLayer.L2;

import com.io.shortly.redirect.webflux.domain.Redirect;
import com.io.shortly.redirect.webflux.domain.RedirectCache;
import com.io.shortly.redirect.webflux.infrastructure.cache.CachedRedirect;
import com.io.shortly.redirect.webflux.infrastructure.metrics.CacheMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Slf4j
@Component("redisCache")
public class RedirectCacheRedisImpl implements RedirectCache {

    private final ReactiveRedisTemplate<String, CachedRedirect> redisTemplate;
    private final CacheMetrics metrics;

    public RedirectCacheRedisImpl(
        ReactiveRedisTemplate<String, CachedRedirect> redisTemplate,
        MeterRegistry meterRegistry
    ) {
        this.redisTemplate = redisTemplate;
        this.metrics = CacheMetrics.of(meterRegistry, L2.getMetricName(), "L2");
    }

    @Override
    public Mono<Void> put(Redirect redirect) {
        String key = L2.buildKey(redirect.getShortCode());

        CachedRedirect cached = CachedRedirect.from(redirect);

        return redisTemplate.opsForValue()
            .set(key, cached, L2.getTtl())
            .doOnSuccess(success -> {
                if (Boolean.TRUE.equals(success)) {
                    metrics.recordPut();
                    log.debug("[Cache:L2] Put: shortCode={}", redirect.getShortCode());
                }
            })
            .then();
    }

    @Override
    public Mono<Redirect> get(String shortCode) {
        String key = L2.buildKey(shortCode);
        return redisTemplate.opsForValue()
            .get(key)
            .map(CachedRedirect::toDomain)
            .doOnNext(redirect -> {
                metrics.recordHit();
                log.debug("[Cache:L2] Hit: shortCode={}", shortCode);
            })
            .switchIfEmpty(Mono.defer(() -> {
                metrics.recordMiss();
                log.debug("[Cache:L2] Miss: shortCode={}", shortCode);
                return Mono.empty();
            }))
            .onErrorResume(error -> {
                log.warn("[Cache:L2] Read failed: shortCode={}, continuing", shortCode);
                return Mono.empty();
            });
    }

    @Override
    public Mono<Void> evict(String shortCode) {
        String key = L2.buildKey(shortCode);
        return redisTemplate.delete(key)
            .doOnSuccess(count -> {
                if (count != null && count > 0) {
                    metrics.recordEvict();
                }
                log.debug("[Cache:L2] Evicted: shortCode={}", shortCode);
            })
            .then();
    }
}
