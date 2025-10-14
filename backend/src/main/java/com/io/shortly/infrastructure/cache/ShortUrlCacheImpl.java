package com.io.shortly.infrastructure.cache;

import com.io.shortly.domain.shorturl.ShortUrl;
import com.io.shortly.domain.shorturl.ShortUrlCache;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Slf4j
@Profile("!phase5")
@Service
public class ShortUrlCacheImpl implements ShortUrlCache {

    private static final String CACHE_NAME = "shortUrls";

    private final CacheManager caffeineCacheManager;
    private final CacheManager redisCacheManager;

    public ShortUrlCacheImpl(
        @Qualifier("caffeineCacheManager") CacheManager caffeineCacheManager,
        @Qualifier("redisCacheManager") CacheManager redisCacheManager
    ) {
        this.caffeineCacheManager = caffeineCacheManager;
        this.redisCacheManager = redisCacheManager;
    }

    @Override
    public Optional<ShortUrl> get(String shortCode) {
        Cache l1Cache = caffeineCacheManager.getCache(CACHE_NAME);
        if (l1Cache != null) {
            ShortUrl cachedResult = l1Cache.get(shortCode, ShortUrl.class);
            if (cachedResult != null) {
                log.debug("URL lookup (L1 Caffeine hit): {}", shortCode);
                return Optional.of(cachedResult);
            }
        }

        Cache l2Cache = redisCacheManager.getCache(CACHE_NAME);
        if (l2Cache != null) {
            ShortUrl redisResult = l2Cache.get(shortCode, ShortUrl.class);
            if (redisResult != null) {
                log.debug("URL lookup (L2 Redis hit): {}", shortCode);
                if (l1Cache != null) {
                    l1Cache.put(shortCode, redisResult);
                }
                return Optional.of(redisResult);
            }
        }

        return Optional.empty();
    }

    @Override
    public void put(ShortUrl shortUrl) {
        Cache l1Cache = caffeineCacheManager.getCache(CACHE_NAME);
        Cache l2Cache = redisCacheManager.getCache(CACHE_NAME);

        if (l1Cache != null) {
            l1Cache.put(shortUrl.getShortUrl(), shortUrl);
        }
        if (l2Cache != null) {
            l2Cache.put(shortUrl.getShortUrl(), shortUrl);
        }

        log.debug("URL cached to L1 & L2: {}", shortUrl.getShortUrl());
    }
}
