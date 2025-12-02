package com.io.shortly.redirect.infrastructure.cache.caffeine;

import static com.io.shortly.redirect.infrastructure.cache.CacheLayer.L1;

import com.github.benmanes.caffeine.cache.LoadingCache;
import com.io.shortly.redirect.domain.Redirect;
import com.io.shortly.redirect.domain.RedirectCache;
import com.io.shortly.redirect.infrastructure.cache.CacheKeyGenerator;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component("caffeineRedirectCache")
public class RedirectCacheCaffeineImpl implements RedirectCache {

    private final LoadingCache<String, Redirect> caffeineCache;

    @Override
    public Optional<Redirect> get(String shortCode) {
        String key = CacheKeyGenerator.generateCacheKey(L1, shortCode);
        return Optional.ofNullable(caffeineCache.get(key));
    }

    @Override
    public void put(Redirect redirect) {
        String key = CacheKeyGenerator.generateCacheKey(L1, redirect.getShortCode());
        caffeineCache.put(key, redirect);
    }
}
