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
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
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

            String shortUrl = shortUrlGenerator.generate(command.originalUrl());

            if (shortUrlRepository.existsByShortUrl(shortUrl)) {
                continue;
            }

            ShortUrl model = shortUrlRepository.save(ShortUrl.of(shortUrl, command.originalUrl()));

            shortUrlCache.put(model);

            return CreateShortUrlResult.of(model.getShortUrl(), model.getOriginalUrl());
        }

        throw new IllegalStateException(
            "Failed to generate unique short code after " + MAX_GENERATION_ATTEMPTS
                + " attempts for URL: " + command.originalUrl()
        );
    }

    public ShortUrlLookupResult findOriginalUrl(ShortUrlLookupCommand command) {
        ShortUrlLookupResult result = shortUrlCache.get(command.shortCode())
            .orElseGet(() -> {
                ShortUrl shortUrl = shortUrlRepository.findByShortUrl(command.shortCode())
                    .orElseThrow(() -> new IllegalArgumentException(
                        "Short code not found: " + command.shortCode())
                    );

                shortUrlCache.put(shortUrl);

                return ShortUrlLookupResult.of(
                    shortUrl.getId(),
                    shortUrl.getOriginalUrl(),
                    shortUrl.getShortUrl()
                );
            });

        clickService.incrementClickCount(result.urlId());

        return result;
    }

}
