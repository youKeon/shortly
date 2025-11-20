package com.io.shortly.url.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.io.shortly.shared.event.UrlCreatedEvent;
import com.io.shortly.url.application.dto.ShortUrlCommand.FindCommand;
import com.io.shortly.url.application.dto.ShortUrlCommand.ShortenCommand;
import com.io.shortly.url.application.dto.ShortUrlResult.ShortenedResult;
import com.io.shortly.url.domain.outbox.Aggregate;
import com.io.shortly.url.domain.outbox.Outbox;
import com.io.shortly.url.domain.outbox.OutboxRepository;
import com.io.shortly.url.domain.url.ShortCodeGenerationFailedException;
import com.io.shortly.url.domain.url.ShortCodeNotFoundException;
import com.io.shortly.url.domain.url.ShortUrl;
import com.io.shortly.url.domain.url.ShortUrlGenerator;
import com.io.shortly.url.domain.url.ShortUrlRepository;
import java.sql.SQLException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.Assert;

@Slf4j
@Service
@RequiredArgsConstructor
public class UrlService {

    private static final int MAX_ATTEMPTS = 5;
    private static final String UNIQUE_VIOLATION_SQL_STATE_PREFIX = "23";

    private final TransactionTemplate transactionTemplate;
    private final ShortUrlRepository shortUrlRepository;
    private final ShortUrlGenerator shortUrlGenerator;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public ShortenedResult shortenUrl(ShortenCommand command) {
        Assert.notNull(command, "Command must not be null");
        Assert.hasText(command.originalUrl(), "Original URL must not be blank");

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

    public ShortenedResult findByShortCode(FindCommand command) {
        Assert.notNull(command, "Command must not be null");
        Assert.hasText(command.shortCode(), "Short code must not be blank");

        String shortCode = command.shortCode();
        ShortUrl shortUrl = shortUrlRepository.findByShortCode(shortCode)
            .orElseThrow(() -> new ShortCodeNotFoundException(shortCode));

        log.debug("URL found: {} -> {}", shortCode, shortUrl.getOriginalUrl());

        return ShortenedResult.of(shortUrl.getShortCode(), shortUrl.getOriginalUrl());
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
        if (cause instanceof SQLException) {
            String sqlState = ((SQLException) cause).getSQLState();
            if (sqlState != null) {
                return sqlState.startsWith(UNIQUE_VIOLATION_SQL_STATE_PREFIX);
            }
        }
        return false;
    }
}
