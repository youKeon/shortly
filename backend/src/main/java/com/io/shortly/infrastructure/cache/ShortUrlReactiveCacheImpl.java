package com.io.shortly.infrastructure.cache;

import com.io.shortly.domain.shorturl.ShortUrl;
import com.io.shortly.domain.shorturl.ShortUrlCache;
import java.time.Duration;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Slf4j
@Profile("phase5")
@Service
@RequiredArgsConstructor
public class ShortUrlReactiveCacheImpl implements ShortUrlCache {

    private static final String CACHE_NAME = "shortUrls";
    private static final String REDIS_KEY_PREFIX = "shortUrl:";
    private static final Duration REDIS_TTL = Duration.ofMinutes(30);

    @Qualifier("caffeineCacheManager")
    private final CacheManager caffeineCacheManager;
    private final ReactiveRedisTemplate<String, ShortUrl> reactiveRedisTemplate;

    @Override
    public Optional<ShortUrl> get(String shortCode) {
        Cache l1Cache = caffeineCacheManager.getCache(CACHE_NAME);
        if (l1Cache != null) {
            ShortUrl cached = l1Cache.get(shortCode, ShortUrl.class);
            if (cached != null) {
                log.debug("Reactive cache hit (Caffeine): {}", shortCode);
                return Optional.of(cached);
            }
        }

        return Mono.defer(() -> reactiveRedisTemplate.opsForValue().get(REDIS_KEY_PREFIX + shortCode))
            .doOnNext(shortUrl -> {
                log.debug("Reactive cache hit (Redis): {}", shortCode);
                if (l1Cache != null) {
                    l1Cache.put(shortCode, shortUrl);
                }
            })
            .blockOptional();
    }

    @Override
    public void put(ShortUrl shortUrl) {
        Cache l1Cache = caffeineCacheManager.getCache(CACHE_NAME);
        if (l1Cache != null) {
            l1Cache.put(shortUrl.getShortUrl(), shortUrl);
        }

        reactiveRedisTemplate.opsForValue()
            .set(REDIS_KEY_PREFIX + shortUrl.getShortUrl(), shortUrl, REDIS_TTL)
            .doOnSuccess(result -> log.debug("Reactive cache update (Redis): {}", shortUrl.getShortUrl()))
            .onErrorResume(ex -> {
                log.warn("Failed to cache short URL to Redis: {}", shortUrl.getShortUrl(), ex);
                return Mono.empty();
            })
            .block();
    }
}

