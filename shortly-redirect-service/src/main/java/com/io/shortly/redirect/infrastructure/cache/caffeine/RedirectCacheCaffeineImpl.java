package com.io.shortly.redirect.infrastructure.cache.caffeine;

import com.github.benmanes.caffeine.cache.Cache;
import com.io.shortly.redirect.domain.Redirect;
import com.io.shortly.redirect.domain.RedirectCache;
import com.io.shortly.redirect.infrastructure.cache.CacheLayer;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Slf4j
@Primary
@Component
@RequiredArgsConstructor
public class RedirectCacheCaffeineImpl implements RedirectCache {

    private static final CacheLayer LAYER = CacheLayer.L1;

    private final Cache<String, Redirect> caffeineCache;

    @Qualifier("redisCache")
    private final RedirectCache redisCache;

    @Override
    public Optional<Redirect> get(String shortCode) {
        // L1 캐시 조회
        String key = LAYER.buildKey(shortCode);
        Redirect l1Result = caffeineCache.getIfPresent(key);

        if (l1Result != null) {
            log.debug("[Cache:L1] Hit: shortCode={}", shortCode);
            return Optional.of(l1Result);
        }

        log.debug("[Cache:L1] Miss: shortCode={}", shortCode);

        // L1 Miss → L2 조회
        Optional<Redirect> l2Result = redisCache.get(shortCode);
        if (l2Result.isPresent()) {
            // L2 Hit → L1 캐시 채우기
            caffeineCache.put(key, l2Result.get());
            log.debug("[Cache:L1] Backfilled from L2: shortCode={}", shortCode);
        }

        return l2Result;
    }

    @Override
    public void put(Redirect redirect) {
        String key = LAYER.buildKey(redirect.getShortCode());
        caffeineCache.put(key, redirect);
        log.debug("[Cache:L1] Put: shortCode={}", redirect.getShortCode());

        try {
            redisCache.put(redirect);
        } catch (Exception e) {
            log.warn("[Cache:L2] Put failed: shortCode={}, continuing", redirect.getShortCode(), e);
        }
    }

    @Override
    public void evict(String shortCode) {
        String key = LAYER.buildKey(shortCode);
        caffeineCache.invalidate(key);
        log.debug("[Cache:L1] Evicted: shortCode={}", shortCode);

        try {
            redisCache.evict(shortCode);
        } catch (Exception e) {
            log.warn("[Cache:L2] Eviction failed: shortCode={}, continuing", shortCode, e);
        }
    }
}
