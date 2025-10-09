package com.io.bitly.application;

import com.io.bitly.application.dto.ShortenUrlCommand.CreateShortUrlCommand;
import com.io.bitly.application.dto.ShortenUrlCommand.ShortUrlLookupCommand;
import com.io.bitly.application.dto.ShortenUrlResult.CreateShortUrlResult;
import com.io.bitly.application.dto.ShortenUrlResult.ShortUrlLookupResult;
import com.io.bitly.domain.shorturl.ShortUrl;
import com.io.bitly.domain.click.UrlClick;
import com.io.bitly.domain.shorturl.ShortUrlRepository;
import com.io.bitly.domain.click.UrlClickRepository;
import com.io.bitly.domain.shorturl.ShortUrlGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShortUrlService {

    private static final int MAX_GENERATION_ATTEMPTS = 5;

    private final ShortUrlRepository shortUrlRepository;
    private final UrlClickRepository urlClickRepository;
    private final ShortUrlGenerator shortUrlGenerator;

    @Transactional
    public CreateShortUrlResult shortenUrl(CreateShortUrlCommand command) {
        String originalUrl = command.originalUrl();

        for (int attempt = 0; attempt < MAX_GENERATION_ATTEMPTS; attempt++) {
            String shortCode = shortUrlGenerator.generate(originalUrl);

            if (shortUrlRepository.existsByShortUrl(shortCode)) {
                continue;
            }

            ShortUrl shortUrl = shortUrlRepository.save(ShortUrl.of(shortCode, originalUrl));

            log.info("URL shortened: {} -> {}", originalUrl, shortCode);
            return CreateShortUrlResult.of(shortUrl.getShortUrl(), shortUrl.getOriginalUrl());
        }

        throw new IllegalStateException(
                "Failed to generate unique short code after " + MAX_GENERATION_ATTEMPTS + " attempts for URL: " + originalUrl
        );
    }

    /**
     * 원본 URL 조회 및 클릭 기록
     */
    @Transactional
    public ShortUrlLookupResult findOriginalUrl(ShortUrlLookupCommand command) {
        String shortCode = command.shortCode();

        ShortUrl shortUrl = shortUrlRepository.findByShortUrl(shortCode)
                .orElseThrow(() -> new IllegalArgumentException("Short code not found: " + shortCode));

        UrlClick click = UrlClick.of(shortUrl.getId());
        urlClickRepository.save(click);

        log.info("URL accessed: {} -> {}", shortCode, shortUrl.getOriginalUrl());

        return ShortUrlLookupResult.of(shortUrl.getId(), shortUrl.getOriginalUrl(), shortUrl.getShortUrl());
    }
}
