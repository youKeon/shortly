package com.io.shortly.application.facade;

import com.io.shortly.application.dto.ShortenUrlCommand.CreateShortUrlCommand;
import com.io.shortly.application.dto.ShortenUrlCommand.ShortUrlLookupCommand;
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

@Slf4j
@Profile("!phase5")
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ShortUrlFacade {

    private static final int MAX_GENERATION_ATTEMPTS = 5;

    private final ShortUrlRepository shortUrlRepository;
    private final ShortUrlGenerator shortUrlGenerator;
    private final ShortUrlCache shortUrlCache;
    private final ClickService clickService;

    @Transactional
    public CreateShortUrlResult shortenUrl(CreateShortUrlCommand command) {
        for (int attempt = 0; attempt < MAX_GENERATION_ATTEMPTS; attempt++) {
            String candidate = shortUrlGenerator.generate(command.originalUrl());

            if (shortUrlRepository.existsByShortUrl(candidate)) {
                continue;
            }

            ShortUrl created = shortUrlRepository.save(ShortUrl.of(candidate, command.originalUrl()));
            shortUrlCache.put(created);

            log.info("URL shortened: {} -> {}", created.getOriginalUrl(), created.getShortUrl());
            return CreateShortUrlResult.of(created.getShortUrl(), created.getOriginalUrl());
        }

        throw new IllegalStateException(
            "Failed to generate unique short code after " + MAX_GENERATION_ATTEMPTS
                + " attempts for URL: " + command.originalUrl()
        );
    }

    public ShortUrlLookupResult findOriginalUrl(ShortUrlLookupCommand command) {
        ShortUrl shortUrl = shortUrlCache.get(command.shortCode())
            .orElseGet(() -> {
                ShortUrl loaded = shortUrlRepository.findByShortUrl(command.shortCode())
                    .orElseThrow(() -> new IllegalArgumentException(
                        "Short code not found: " + command.shortCode()
                    ));

                shortUrlCache.put(loaded);
                return loaded;
            });

        clickService.incrementClickCount(shortUrl.getId());

        log.info("URL accessed: {} -> {}", command.shortCode(), shortUrl.getOriginalUrl());
        return ShortUrlLookupResult.of(
            shortUrl.getId(),
            shortUrl.getOriginalUrl(),
            shortUrl.getShortUrl()
        );
    }

}
