package com.io.shortly.redirect.infrastructure.cache.caffeine;

import static com.io.shortly.redirect.infrastructure.cache.CacheLayer.L1;

import com.github.benmanes.caffeine.cache.Cache;
import com.io.shortly.redirect.domain.Redirect;
import com.io.shortly.redirect.domain.RedirectCacheService;
import com.io.shortly.redirect.infrastructure.cache.CacheKeyGenerator;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Slf4j
@Primary
@Component
public class RedirectCacheServiceCaffeineImpl implements RedirectCacheService {

    private final Cache<String, Redirect> caffeineCache;
    private final RedirectCacheService redisCache;

    public RedirectCacheServiceCaffeineImpl(
            Cache<String, Redirect> caffeineCache,
            @Qualifier("redisCache") RedirectCacheService redisCache
    ) {
        this.caffeineCache = caffeineCache;
        this.redisCache = redisCache;
    }

    @Override
    public Optional<Redirect> get(String shortCode) {
        // L1 캐시 조회
        String key = CacheKeyGenerator.generateCacheKey(L1, shortCode);
        Redirect l1Result = caffeineCache.getIfPresent(key);

        if (l1Result != null) {
            log.debug("[Cache:L1] 히트: shortCode={}", shortCode);
            return Optional.of(l1Result);
        }

        log.debug("[Cache:L1] 미스: shortCode={}", shortCode);

        // L1 Miss → L2 조회
        Optional<Redirect> l2Result = redisCache.get(shortCode);
        if (l2Result.isPresent()) {
            // L2 Hit → L1 캐시 채우기
            caffeineCache.put(key, l2Result.get());
            log.debug("[Cache:L1] L2에서 백필 완료: shortCode={}", shortCode);
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
}
