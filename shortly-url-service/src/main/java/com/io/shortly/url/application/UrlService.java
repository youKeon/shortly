package com.io.shortly.url.application;

import com.io.shortly.url.application.dto.ShortUrlCommand.ShortenCommand;
import com.io.shortly.url.application.dto.ShortUrlResult.ShortenedResult;
import com.io.shortly.url.domain.ShortCodeGenerationFailedException;
import com.io.shortly.url.domain.ShortUrl;
import com.io.shortly.url.domain.ShortUrlGenerator;
import com.io.shortly.url.domain.ShortUrlRepository;
import com.io.shortly.url.domain.event.UrlCreatedDomainEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class UrlService {

    private static final int MAX_ATTEMPTS = 5;

    private final ShortUrlRepository shortUrlRepository;
    private final ShortUrlGenerator shortUrlGenerator;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate txTemplate;

    public ShortenedResult shortenUrl(ShortenCommand command) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            String candidate = shortUrlGenerator.generate(command.originalUrl());

            try {
                ShortUrl url = txTemplate.execute(status -> {
                    ShortUrl shortUrl = shortUrlRepository.save(
                        ShortUrl.create(candidate, command.originalUrl())
                    );

                    log.info("URL shortened: {} -> {}", command.originalUrl(), shortUrl.getShortCode());

                    eventPublisher.publishEvent(
                        UrlCreatedDomainEvent.of(shortUrl.getShortCode(), shortUrl.getOriginalUrl())
                    );

                    return shortUrl;
                });

                return ShortenedResult.of(url.getShortCode(), url.getOriginalUrl());

            } catch (DataIntegrityViolationException e) {

                if (isDuplicatedException(e)) {
                    log.debug("Duplicate short code '{}', retrying...", candidate);
                    continue;
                }

                throw new IllegalStateException("Database constraint violation", e);
            }
        }

        log.error("Failed to generate unique short code");
        throw new ShortCodeGenerationFailedException(MAX_ATTEMPTS);
    }

    private boolean isDuplicatedException(DataIntegrityViolationException e) {
        Throwable cause = e.getMostSpecificCause();
        String message = cause.getMessage();

        return
            message != null && (
                message.contains("unique") ||
                    message.contains("UK_") ||
                    message.contains("duplicate key") ||
                    message.contains("Duplicate entry")
            );
    }
}
