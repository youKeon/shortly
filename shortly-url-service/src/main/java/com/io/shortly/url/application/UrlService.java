package com.io.shortly.url.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.io.shortly.shared.event.UrlCreatedEvent;
import com.io.shortly.url.application.dto.ShortUrlCommand.ShortenCommand;
import com.io.shortly.url.application.dto.ShortUrlResult.ShortenedResult;
import com.io.shortly.url.domain.event.Aggregate;
import com.io.shortly.url.domain.event.Outbox;
import com.io.shortly.url.domain.event.OutboxRepository;
import com.io.shortly.url.domain.url.ShortCodeGenerationFailedException;
import com.io.shortly.url.domain.url.ShortUrl;
import com.io.shortly.url.domain.url.ShortUrlGenerator;
import com.io.shortly.url.domain.url.ShortUrlRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public ShortenedResult shortenUrl(ShortenCommand command) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            String candidate = shortUrlGenerator.generate(command.originalUrl());

            try {
                return transactionTemplate.execute(status -> {
                    // 단축 URL 저장
                    ShortUrl shortUrl = ShortUrl.create(candidate, command.originalUrl());
                    shortUrlRepository.save(shortUrl);

                    log.info("URL shortened: {} -> {}", command.originalUrl(), shortUrl.getShortCode());

                    // 이벤트 저장
                    saveEvent(shortUrl);

                    return ShortenedResult.of(shortUrl.getShortCode(), shortUrl.getOriginalUrl());
                });

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

    private void saveEvent(ShortUrl shortUrl) {
        try {
            UrlCreatedEvent event = UrlCreatedEvent.of(
                shortUrl.getShortCode(),
                shortUrl.getOriginalUrl()
            );

            String payload = objectMapper.writeValueAsString(event);

            Outbox outbox = Outbox.create(
                Aggregate.URL,
                shortUrl.getShortCode(),
                payload
            );

            outboxRepository.save(outbox);

            log.debug("Outbox event saved for shortCode: {}", shortUrl.getShortCode());

        } catch (JsonProcessingException e) {
            log.error("Failed to serialize UrlCreatedEvent", e);
            throw new IllegalStateException("Failed to create outbox event", e);
        }
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
