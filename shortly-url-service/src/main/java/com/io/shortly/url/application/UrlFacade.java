package com.io.shortly.url.application;

import com.io.shortly.shared.event.UrlCreatedEvent;
import com.io.shortly.url.application.dto.ShortUrlCommand.FindCommand;
import com.io.shortly.url.application.dto.ShortUrlCommand.ShortenCommand;
import com.io.shortly.url.application.dto.ShortUrlResult.ShortenedResult;
import com.io.shortly.url.domain.ShortCodeNotFoundException;
import com.io.shortly.url.domain.ShortUrl;
import com.io.shortly.url.domain.ShortUrlEventPublisher;
import com.io.shortly.url.domain.ShortUrlGenerator;
import com.io.shortly.url.domain.ShortUrlRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

@Slf4j
@Service
@RequiredArgsConstructor
public class UrlFacade {

    private final ShortUrlRepository shortUrlRepository;
    private final ShortUrlGenerator shortUrlGenerator;
    private final ShortUrlEventPublisher eventPublisher;

    @Transactional
    public ShortenedResult shortenUrl(ShortenCommand command) {
        Assert.notNull(command, "Command must not be null");
        Assert.hasText(command.originalUrl(), "Original URL must not be blank");

        var generated = shortUrlGenerator.generate(command.originalUrl());

        ShortUrl shortUrl = ShortUrl.create(generated.shortCode(), command.originalUrl());
        shortUrlRepository.save(shortUrl);

        log.info("URL shortened: {} -> {} ", command.originalUrl(), shortUrl.getShortCode());

        UrlCreatedEvent event = UrlCreatedEvent.of(
            generated.snowflakeId(),
            shortUrl.getShortCode(),
            shortUrl.getOriginalUrl()
        );

        eventPublisher.publishUrlCreated(event);

        return ShortenedResult.of(shortUrl.getShortCode(), shortUrl.getOriginalUrl());
    }

    @Transactional(readOnly = true)
    public ShortenedResult findByShortCode(FindCommand command) {
        Assert.notNull(command, "Command must not be null");
        Assert.hasText(command.shortCode(), "Short code must not be blank");

        String shortCode = command.shortCode();
        ShortUrl shortUrl = shortUrlRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new ShortCodeNotFoundException(shortCode));

        log.debug("URL found: {} -> {}", shortCode, shortUrl.getOriginalUrl());

        return ShortenedResult.of(shortUrl.getShortCode(), shortUrl.getOriginalUrl());
    }
}
