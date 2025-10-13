package com.io.bitly.application;

import com.io.bitly.application.dto.ShortenUrlResult.ShortUrlLookupResult;
import com.io.bitly.domain.shorturl.ShortUrl;
import com.io.bitly.domain.shorturl.ShortUrlRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class UrlCacheService {

    private static final String CACHE_NAME = "shortUrls";

    private final ShortUrlRepository shortUrlRepository;
    private final CacheManager caffeineCacheManager;
    private final CacheManager redisCacheManager;

    public UrlCacheService(
            ShortUrlRepository shortUrlRepository,
            @Qualifier("caffeineCacheManager") CacheManager caffeineCacheManager,
            @Qualifier("redisCacheManager") CacheManager redisCacheManager
    ) {
        this.shortUrlRepository = shortUrlRepository;
        this.caffeineCacheManager = caffeineCacheManager;
        this.redisCacheManager = redisCacheManager;
    }

    @Transactional(readOnly = true)
    public ShortUrlLookupResult findByShortCode(String shortCode) {
        // 1. L1 캐시(Caffeine) 조회
        Cache l1Cache = caffeineCacheManager.getCache(CACHE_NAME);
        if (l1Cache != null) {
            ShortUrlLookupResult cachedResult = l1Cache.get(shortCode, ShortUrlLookupResult.class);
            if (cachedResult != null) {
                log.debug("URL lookup (L1 Caffeine hit): {}", shortCode);
                return cachedResult;
            }
        }

        // 2. L2 캐시(Redis) 조회
        Cache l2Cache = redisCacheManager.getCache(CACHE_NAME);
        if (l2Cache != null) {
            ShortUrlLookupResult redisResult = l2Cache.get(shortCode, ShortUrlLookupResult.class);
            if (redisResult != null) {
                log.debug("URL lookup (L2 Redis hit): {}", shortCode);
                // L1 캐시에 저장
                if (l1Cache != null) {
                    l1Cache.put(shortCode, redisResult);
                }
                return redisResult;
            }
        }

        // 3. DB 조회
        ShortUrl shortUrl = shortUrlRepository.findByShortUrl(shortCode)
                .orElseThrow(() -> new IllegalArgumentException("Short code not found: " + shortCode));

        ShortUrlLookupResult dbResult = ShortUrlLookupResult.of(
                shortUrl.getId(),
                shortUrl.getOriginalUrl(),
                shortUrl.getShortUrl()
        );

        // L1, L2 캐시에 저장
        if (l1Cache != null) {
            l1Cache.put(shortCode, dbResult);
        }
        if (l2Cache != null) {
            l2Cache.put(shortCode, dbResult);
        }

        log.debug("URL lookup (DB hit, cached to L1 & L2): {}", shortCode);
        return dbResult;
    }

    /**
     * 새로 생성된 URL을 L1, L2 캐시에 저장
     */
    public void saveCacheData(ShortUrlLookupResult result) {
        Cache l1Cache = caffeineCacheManager.getCache(CACHE_NAME);
        Cache l2Cache = redisCacheManager.getCache(CACHE_NAME);

        if (l1Cache != null) {
            l1Cache.put(result.shortCode(), result);
        }
        if (l2Cache != null) {
            l2Cache.put(result.shortCode(), result);
        }

        log.debug("URL cached to L1 & L2: {}", result.shortCode());
    }
}
