package com.io.shortly.redirect.infrastructure.redis.cache;

import static com.io.shortly.redirect.infrastructure.redis.cache.CacheLayer.L1;

import com.github.benmanes.caffeine.cache.Cache;
import com.io.shortly.redirect.domain.Redirect;
import com.io.shortly.redirect.domain.RedirectCache;
import java.util.Optional;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Slf4j
@Primary
@Component
public class RedirectCacheCaffeineImpl implements RedirectCache {

    private final Cache<String, Redirect> caffeineCache;
    private final RedirectCache redisCache;

    public RedirectCacheCaffeineImpl(
            Cache<String, Redirect> caffeineCache,
            @Qualifier("redisCache") RedirectCache redisCache) {
        this.caffeineCache = caffeineCache;
        this.redisCache = redisCache;
    }

    @Override
    public Optional<Redirect> get(String shortCode) {
        // L1 캐시 조회
        String key = CacheKeyGenerator.generateCacheKey(L1, shortCode);
        Redirect l1Result = caffeineCache.getIfPresent(key);

        if (l1Result != null) {
            log.info("[Cache:L1] HIT - shortCode={}, targetUrl={}", shortCode, l1Result.getTargetUrl());
            return Optional.of(l1Result);
        }

        log.info("[Cache:L1] MISS - shortCode={}", shortCode);

        // L1 Miss → L2 조회
        Optional<Redirect> l2Result = redisCache.get(shortCode);
        if (l2Result.isPresent()) {
            // L2 Hit → L1 캐시 채우기
            caffeineCache.put(key, l2Result.get());
            log.info("[Cache:L1] L2 BackFill - shortCode={}", shortCode);
        }

        return l2Result;
    }

    @Override
    public void put(Redirect redirect) {
        String key = CacheKeyGenerator.generateCacheKey(L1, redirect.getShortCode());
        caffeineCache.put(key, redirect);
        log.debug("[Cache:L1] 저장 완료: shortCode={}", redirect.getShortCode());

        try {
            redisCache.put(redirect);
        } catch (Exception e) {
            log.warn("[Cache:L2] 저장 실패: shortCode={}, 계속 진행", redirect.getShortCode(), e);
        }
    }

    @Override
    public Redirect get(String shortCode, Function<String, Redirect> loader) {
        String key = CacheKeyGenerator.generateCacheKey(L1, shortCode);

        // Atomic
        return caffeineCache.get(key, k -> {
            // 1. L2 조회
            Optional<Redirect> l2Result = redisCache.get(shortCode);
            if (l2Result.isPresent()) {
                return l2Result.get();
            }

            // 2. L2 Miss -> DB Loader 실행 (단 한 번만 실행됨)
            Redirect loaded = loader.apply(shortCode);
            if (loaded != null) {
                // 3. L2 저장
                try {
                    redisCache.put(loaded);
                } catch (Exception e) {
                    log.warn("[Cache:L2] 저장 실패: shortCode={}, 계속 진행", shortCode, e);
                }
            }
            return loaded;
        });
    }
}
