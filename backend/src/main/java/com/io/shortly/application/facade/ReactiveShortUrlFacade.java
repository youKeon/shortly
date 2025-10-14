package com.io.shortly.application.facade;

import com.io.shortly.application.dto.ShortenUrlResult.CreateShortUrlResult;
import com.io.shortly.application.dto.ShortenUrlResult.ShortUrlLookupResult;
import com.io.shortly.domain.shorturl.ShortUrl;
import com.io.shortly.domain.shorturl.ShortUrlGenerator;
import com.io.shortly.domain.shorturl.ShortUrlRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;

@Slf4j
@Profile("phase5")
@Service
@RequiredArgsConstructor
public class ReactiveShortUrlFacade {

    private static final int MAX_GENERATION_ATTEMPTS = 5;

    private final ShortUrlRepository shortUrlRepository;
    private final ShortUrlGenerator shortUrlGenerator;
    private final CacheManager caffeineCacheManager;
    private final ReactiveRedisTemplate<String, ShortUrlLookupResult> reactiveRedisTemplate;

    private static final String CACHE_NAME = "shortUrls";
    private static final String REDIS_KEY_PREFIX = "shortUrl:";
    private static final Duration REDIS_TTL = Duration.ofMinutes(30);

    @Transactional
    public Mono<CreateShortUrlResult> shortenUrl(String originalUrl) {
        return Mono.fromCallable(() -> createShortUrl(originalUrl))
            .subscribeOn(Schedulers.boundedElastic())
            .map(shortUrl -> {
                ShortUrlLookupResult lookupResult = ShortUrlLookupResult.of(
                    shortUrl.getId(),
                    shortUrl.getOriginalUrl(),
                    shortUrl.getShortUrl()
                );

                var cache = caffeineCacheManager.getCache(CACHE_NAME);
                if (cache != null) {
                    cache.put(shortUrl.getShortUrl(), lookupResult);
                }

                log.info("URL shortened (reactive): {} -> {}", shortUrl.getOriginalUrl(), shortUrl.getShortUrl());
                return CreateShortUrlResult.of(shortUrl.getShortUrl(), shortUrl.getOriginalUrl());
            })
            .flatMap(result -> reactiveRedisTemplate.opsForValue()
                .set(REDIS_KEY_PREFIX + result.shortCode(),
                    ShortUrlLookupResult.of(null, result.originalUrl(), result.shortCode()),
                    REDIS_TTL)
                .thenReturn(result));
    }

    public Mono<ShortUrlLookupResult> getOriginalUrl(String shortCode) {
        return Mono.justOrEmpty(getCaffeineCache(shortCode))
            .switchIfEmpty(Mono.defer(() -> getRedisCache(shortCode)
                .switchIfEmpty(Mono.defer(() -> getDatabaseRecord(shortCode)))));
    }

    private ShortUrlLookupResult getCaffeineCache(String shortCode) {
        var cache = caffeineCacheManager.getCache(CACHE_NAME);
        return cache != null ? cache.get(shortCode, ShortUrlLookupResult.class) : null;
    }

    private ShortUrl createShortUrl(String originalUrl) {
        for (int attempt = 0; attempt < MAX_GENERATION_ATTEMPTS; attempt++) {
            String shortCode = shortUrlGenerator.generate(originalUrl);

            if (shortUrlRepository.existsByShortUrl(shortCode)) {
                continue;
            }

            ShortUrl shortUrl = ShortUrl.of(shortCode, originalUrl);
            return shortUrlRepository.save(shortUrl);
        }

        throw new IllegalStateException(
            "Failed to generate unique short code after " + MAX_GENERATION_ATTEMPTS + " attempts for URL: " + originalUrl
        );
    }

    private Mono<ShortUrlLookupResult> getRedisCache(String shortCode) {
        return reactiveRedisTemplate.opsForValue()
            .get(REDIS_KEY_PREFIX + shortCode)
            .doOnNext(result -> {
                var cache = caffeineCacheManager.getCache(CACHE_NAME);
                if (cache != null) {
                    cache.put(shortCode, result);
                }
            });
    }

    private Mono<ShortUrlLookupResult> getDatabaseRecord(String shortCode) {
        return Mono.fromCallable(() -> shortUrlRepository.findByShortUrl(shortCode)
                .orElseThrow(() -> new IllegalArgumentException("Short code not found: " + shortCode)))
            .subscribeOn(Schedulers.boundedElastic())
            .map(this::toLookupResult)
            .flatMap(result -> cacheResult(result).thenReturn(result));
    }

    private Mono<Void> cacheResult(ShortUrlLookupResult result) {
        return Mono.fromRunnable(() -> {
                var cache = caffeineCacheManager.getCache(CACHE_NAME);
                if (cache != null) {
                    cache.put(result.shortCode(), result);
                }
            })
            .then(reactiveRedisTemplate.opsForValue()
                .set(REDIS_KEY_PREFIX + result.shortCode(), result, REDIS_TTL)
            )
            .then();
    }

    private ShortUrlLookupResult toLookupResult(ShortUrl shortUrl) {
        return ShortUrlLookupResult.of(
            shortUrl.getId(),
            shortUrl.getOriginalUrl(),
            shortUrl.getShortUrl()
        );
    }
}
