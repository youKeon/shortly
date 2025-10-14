package com.io.shortly.infrastructure.shorturl;

import com.io.shortly.domain.shorturl.ReactiveShortUrlCache;
import com.io.shortly.domain.shorturl.ShortUrl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Slf4j
@Profile("phase5")
@Service
@RequiredArgsConstructor
public class ReactiveShortUrlCacheImpl implements ReactiveShortUrlCache {

    private static final String CACHE_NAME = "shortUrls";

    @Qualifier("caffeineCacheManager")
    private final CacheManager caffeineCacheManager;
    @Qualifier("redisCacheManager")
    private final CacheManager redisCacheManager;

    @Override
    public Mono<ShortUrl> get(String shortCode) {
        return getFromCache(caffeineCacheManager, shortCode)
            .switchIfEmpty(Mono.defer(() -> getFromCache(redisCacheManager, shortCode)
                .flatMap(shortUrl -> putIntoCache(caffeineCacheManager, shortUrl)
                    .thenReturn(shortUrl)
                )
            ));
    }

    @Override
    public Mono<Void> put(ShortUrl shortUrl) {
        return Mono.when(
                putIntoCache(caffeineCacheManager, shortUrl),
                putIntoCache(redisCacheManager, shortUrl)
            )
            .then();
    }

    private Mono<ShortUrl> getFromCache(CacheManager cacheManager, String shortCode) {
        if (cacheManager == null) {
            return Mono.empty();
        }

        return Mono.fromCallable(() -> {
                Cache cache = cacheManager.getCache(CACHE_NAME);
                if (cache == null) {
                    return null;
                }

                ShortUrl cached = cache.get(shortCode, ShortUrl.class);
                if (cached != null) {
                    log.debug("Reactive cache hit ({}): {}", cacheManager.getClass().getSimpleName(), shortCode);
                }
                return cached;
            })
            .subscribeOn(Schedulers.boundedElastic())
            .flatMap(result -> result != null ? Mono.just(result) : Mono.empty());
    }

    private Mono<Void> putIntoCache(CacheManager cacheManager, ShortUrl shortUrl) {
        if (cacheManager == null) {
            return Mono.empty();
        }

        return Mono.fromRunnable(() -> {
                Cache cache = cacheManager.getCache(CACHE_NAME);
                if (cache != null) {
                    cache.put(shortUrl.getShortUrl(), shortUrl);
                    log.debug("Reactive cache update ({}): {}", cacheManager.getClass().getSimpleName(), shortUrl.getShortUrl());
                }
            })
            .subscribeOn(Schedulers.boundedElastic())
            .then();
    }
}

