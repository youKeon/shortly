package com.io.bitly.application;

import com.io.bitly.application.dto.ShortenUrlCommand.CreateShortUrlCommand;
import com.io.bitly.application.dto.ShortenUrlCommand.ShortUrlLookupCommand;
import com.io.bitly.application.dto.ShortenUrlResult.CreateShortUrlResult;
import com.io.bitly.application.dto.ShortenUrlResult.ShortUrlLookupResult;
import com.io.bitly.domain.shorturl.ShortUrl;
import com.io.bitly.domain.shorturl.ShortUrlRepository;
import com.io.bitly.domain.shorturl.ShortUrlGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ShortUrlService {

    private static final int MAX_GENERATION_ATTEMPTS = 5;

    private final ShortUrlRepository shortUrlRepository;
    private final ShortUrlGenerator shortUrlGenerator;
    private final UrlCacheService urlCacheService;
    private final UrlClickService urlClickService;

    @Transactional
    public CreateShortUrlResult shortenUrl(CreateShortUrlCommand command) {
        String originalUrl = command.originalUrl();

        for (int attempt = 0; attempt < MAX_GENERATION_ATTEMPTS; attempt++) {
            String shortCode = shortUrlGenerator.generate(originalUrl);

            if (shortUrlRepository.existsByShortUrl(shortCode)) {
                continue;
            }

            ShortUrl shortUrl = shortUrlRepository.save(ShortUrl.of(shortCode, originalUrl));

            ShortUrlLookupResult lookupResult = ShortUrlLookupResult.of(
                    shortUrl.getId(),
                    shortUrl.getOriginalUrl(),
                    shortUrl.getShortUrl()
            );
            urlCacheService.saveCacheData(lookupResult);

            log.info("URL shortened: {} -> {}", originalUrl, shortCode);
            return CreateShortUrlResult.of(shortUrl.getShortUrl(), shortUrl.getOriginalUrl());
        }

        throw new IllegalStateException(
                "Failed to generate unique short code after " + MAX_GENERATION_ATTEMPTS + " attempts for URL: " + originalUrl
        );
    }

    public ShortUrlLookupResult findOriginalUrl(ShortUrlLookupCommand command) {
        String shortCode = command.shortCode();

        ShortUrlLookupResult result = urlCacheService.findByShortCode(shortCode);

        urlClickService.incrementClickCount(result.urlId());

        log.info("URL accessed: {} -> {}", shortCode, result.originalUrl());

        return result;
    }
}
