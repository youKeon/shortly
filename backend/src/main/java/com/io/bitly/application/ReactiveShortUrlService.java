package com.io.bitly.application;

import com.io.bitly.application.dto.ShortenUrlCommand.CreateShortUrlCommand;
import com.io.bitly.application.dto.ShortenUrlCommand.ShortUrlLookupCommand;
import com.io.bitly.application.dto.ShortenUrlResult.CreateShortUrlResult;
import com.io.bitly.application.dto.ShortenUrlResult.ShortUrlLookupResult;
import com.io.bitly.domain.shorturl.ShortUrl;
import com.io.bitly.domain.shorturl.ShortUrlGenerator;
import com.io.bitly.domain.shorturl.ShortUrlRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
@Profile("phase4")
public class ReactiveShortUrlService {

    private static final int MAX_GENERATION_ATTEMPTS = 5;
    private static final String CACHE_NAME = "shortUrls";
    private static final Duration REDIS_TTL = Duration.ofMinutes(10);

    private final ShortUrlRepository shortUrlRepository;
    private final ShortUrlGenerator shortUrlGenerator;
    private final CacheManager caffeineCacheManager;
    private final ReactiveRedisTemplate<String, ShortUrlLookupResult> reactiveRedisTemplate;
    private final ReactiveUrlClickService reactiveUrlClickService;

    public Mono<CreateShortUrlResult> shortenUrl(CreateShortUrlCommand command) {
        String originalUrl = command.originalUrl();

        return Mono.defer(() -> {
                    for (int attempt = 0; attempt < MAX_GENERATION_ATTEMPTS; attempt++) {
                        String shortCode = shortUrlGenerator.generate(originalUrl);
                        if (!shortUrlRepository.existsByShortUrl(shortCode)) {
                            return Mono.just(shortCode);
                        }
                    }
                    return Mono.error(new IllegalStateException(
                            "Failed to generate unique short code after " + MAX_GENERATION_ATTEMPTS + " attempts"
                    ));
                })
                .flatMap(shortCode -> Mono.fromCallable(() -> shortUrlRepository.save(ShortUrl.of(shortCode, originalUrl)))
                        .subscribeOn(Schedulers.boundedElastic())
                        .flatMap(savedShortUrl -> {
                            ShortUrlLookupResult lookupResult = ShortUrlLookupResult.of(
                                    savedShortUrl.getId(), savedShortUrl.getOriginalUrl(), savedShortUrl.getShortUrl()
                            );
                            return reactiveRedisTemplate.opsForValue()
                                    .set(CACHE_NAME + "::" + shortCode, lookupResult, REDIS_TTL)
                                    .doOnSuccess(v -> {
                                        Cache caffeineCache = caffeineCacheManager.getCache(CACHE_NAME);
                                        if (caffeineCache != null) {
                                            caffeineCache.put(shortCode, lookupResult);
                                        }
                                        log.debug("URL shortened and cached: {} -> {}", originalUrl, shortCode);
                                    })
                                    .thenReturn(CreateShortUrlResult.of(shortCode, originalUrl));
                        }));
    }

    public Mono<ShortUrlLookupResult> findOriginalUrl(ShortUrlLookupCommand command) {
        String shortCode = command.shortCode();
        Cache caffeineCache = caffeineCacheManager.getCache(CACHE_NAME);

        // 1. Caffeine L1 캐시 조회
        ShortUrlLookupResult cachedResult = (caffeineCache != null) ? caffeineCache.get(shortCode, ShortUrlLookupResult.class) : null;
        if (cachedResult != null) {
            log.debug("URL lookup (Caffeine hit): {}", shortCode);
            reactiveUrlClickService.incrementClickCount(cachedResult.urlId()).subscribe();
            return Mono.just(cachedResult);
        }

        // 2. Redis L2 캐시 조회
        return reactiveRedisTemplate.opsForValue().get(CACHE_NAME + "::" + shortCode)
                .flatMap(redisResult -> {
                    log.debug("URL lookup (Redis hit): {}", shortCode);
                    if (caffeineCache != null) {
                        caffeineCache.put(shortCode, redisResult);
                    }
                    reactiveUrlClickService.incrementClickCount(redisResult.urlId()).subscribe();
                    return Mono.just(redisResult);
                })
                // 3. DB 조회
                .switchIfEmpty(Mono.fromCallable(() -> shortUrlRepository.findByShortUrl(shortCode)
                                .orElseThrow(() -> new IllegalArgumentException("Short code not found: " + shortCode)))
                        .subscribeOn(Schedulers.boundedElastic())
                        .flatMap(dbResult -> {
                            ShortUrlLookupResult lookupResult = ShortUrlLookupResult.of(
                                    dbResult.getId(), dbResult.getOriginalUrl(), dbResult.getShortUrl()
                            );
                            return reactiveRedisTemplate.opsForValue()
                                    .set(CACHE_NAME + "::" + shortCode, lookupResult, REDIS_TTL)
                                    .doOnSuccess(v -> {
                                        if (caffeineCache != null) {
                                            caffeineCache.put(shortCode, lookupResult);
                                        }
                                        log.debug("URL lookup (DB hit, cached): {}", shortCode);
                                    })
                                    .thenReturn(lookupResult);
                        }))
                .doOnNext(result -> log.debug("URL accessed: {} -> {}", shortCode, result.originalUrl()));
    }
}

