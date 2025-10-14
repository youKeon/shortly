package com.io.shortly.application.facade;

import com.io.shortly.application.dto.ShortenUrlResult.CreateShortUrlResult;
import com.io.shortly.application.dto.ShortenUrlResult.ShortUrlLookupResult;
import com.io.shortly.domain.click.ClickService;
import com.io.shortly.domain.shorturl.ShortUrl;
import com.io.shortly.domain.shorturl.ShortUrlCache;
import com.io.shortly.domain.shorturl.ShortUrlGenerator;
import com.io.shortly.domain.shorturl.ShortUrlRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Slf4j
@Profile("phase5")
@Service
@RequiredArgsConstructor
public class ReactiveShortUrlFacade {

    private static final int MAX_GENERATION_ATTEMPTS = 5;

    private final ShortUrlRepository shortUrlRepository;
    private final ShortUrlGenerator shortUrlGenerator;
    private final ShortUrlCache shortUrlCache;
    private final ClickService clickService;

    @Transactional
    public Mono<CreateShortUrlResult> shortenUrl(String originalUrl) {
        return Mono.fromCallable(() -> {
                for (int attempt = 0; attempt < MAX_GENERATION_ATTEMPTS; attempt++) {
                    String shortCode = shortUrlGenerator.generate(originalUrl);

                    if (shortUrlRepository.existsByShortUrl(shortCode)) {
                        continue;
                    }

                    ShortUrl created = shortUrlRepository.save(ShortUrl.of(shortCode, originalUrl));
                    shortUrlCache.put(created);
                    return created;
                }

                throw new IllegalStateException(
                    "Failed to generate unique short code after " + MAX_GENERATION_ATTEMPTS
                        + " attempts for URL: " + originalUrl
                );
            })
            .subscribeOn(Schedulers.boundedElastic())
            .map(shortUrl -> CreateShortUrlResult.of(shortUrl.getShortUrl(), shortUrl.getOriginalUrl()))
            .doOnSuccess(result ->
                log.info("URL shortened (reactive): {} -> {}", result.originalUrl(), result.shortCode())
            );
    }

    public Mono<ShortUrlLookupResult> getOriginalUrl(String shortCode) {
        Mono<ShortUrl> cached = Mono.fromCallable(() -> shortUrlCache.get(shortCode))
            .subscribeOn(Schedulers.boundedElastic())
            .flatMap(optional -> optional.map(Mono::just).orElseGet(Mono::empty));

        Mono<ShortUrl> loaded = Mono.fromCallable(() -> shortUrlRepository.findByShortUrl(shortCode)
                .orElseThrow(() -> new IllegalArgumentException("Short code not found: " + shortCode)))
            .subscribeOn(Schedulers.boundedElastic())
            .doOnNext(shortUrlCache::put);

        return cached.switchIfEmpty(Mono.defer(() -> loaded))
            .flatMap(shortUrl -> {
                if (shortUrl.getId() == null) {
                    return Mono.just(shortUrl);
                }

                return Mono.fromRunnable(() -> clickService.incrementClickCount(shortUrl.getId()))
                    .subscribeOn(Schedulers.boundedElastic())
                    .doOnError(ex -> log.warn("Failed to increment click count for urlId={}", shortUrl.getId(), ex))
                    .onErrorResume(ex -> Mono.empty())
                    .thenReturn(shortUrl);
            })
            .map(shortUrl -> {
                log.info("URL accessed (reactive): {} -> {}", shortCode, shortUrl.getOriginalUrl());
                return ShortUrlLookupResult.of(
                    shortUrl.getId(),
                    shortUrl.getOriginalUrl(),
                    shortUrl.getShortUrl()
                );
            });
    }
}
