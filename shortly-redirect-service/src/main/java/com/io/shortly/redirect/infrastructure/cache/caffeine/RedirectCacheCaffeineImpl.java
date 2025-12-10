package com.io.shortly.redirect.infrastructure.cache.caffeine;

import static com.io.shortly.redirect.infrastructure.cache.CacheLayer.L1;

import com.github.benmanes.caffeine.cache.Cache;
import com.io.shortly.redirect.domain.Redirect;
import com.io.shortly.redirect.domain.RedirectCache;
import com.io.shortly.redirect.infrastructure.cache.CacheKeyGenerator;
import java.util.Optional;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component("caffeineRedirectCache")
public class RedirectCacheCaffeineImpl implements RedirectCache {

    private final Cache<String, Redirect> caffeineCache;

    @Override
    public Optional<Redirect> get(String shortCode) {
        String key = CacheKeyGenerator.generateCacheKey(L1, shortCode);
        return Optional.ofNullable(caffeineCache.getIfPresent(key));
    }

    @Override
    public Redirect getOrLoad(String shortCode, Supplier<Redirect> loader) {
        String key = CacheKeyGenerator.generateCacheKey(L1, shortCode);
        return caffeineCache.get(key, k -> loader.get());
    }

    @Override
    public void put(Redirect redirect) {
        String key = CacheKeyGenerator.generateCacheKey(L1, redirect.getShortCode());
        caffeineCache.put(key, redirect);
    }
}
