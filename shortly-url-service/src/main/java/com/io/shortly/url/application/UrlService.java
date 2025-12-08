package com.io.shortly.url.application;

import com.io.shortly.shared.event.UrlCreatedEvent;
import com.io.shortly.shared.kafka.TopicType;
import com.io.shortly.url.application.dto.ShortUrlCommand.FindCommand;
import com.io.shortly.url.application.dto.ShortUrlCommand.ShortenCommand;
import com.io.shortly.url.application.dto.ShortUrlResult.ShortenedResult;
import com.io.shortly.url.domain.url.ShortCodeNotFoundException;
import com.io.shortly.url.domain.url.ShortUrl;
import com.io.shortly.url.domain.url.ShortUrlGenerator;
import com.io.shortly.url.domain.url.ShortUrlRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

@Slf4j
@Service
@RequiredArgsConstructor
public class UrlService {

    private final ShortUrlRepository shortUrlRepository;
    private final ShortUrlGenerator shortUrlGenerator;
    private final RedisTemplate<String, UrlCreatedEvent> redisTemplate;

    @Transactional
    public ShortenedResult shortenUrl(ShortenCommand command) {
        Assert.notNull(command, "Command must not be null");
        Assert.hasText(command.originalUrl(), "Original URL must not be blank");

        var generated = shortUrlGenerator.generate(command.originalUrl());

        // 단축 URL 저장
        ShortUrl shortUrl = ShortUrl.create(generated.shortCode(), command.originalUrl());
        shortUrlRepository.save(shortUrl);

        log.info("URL shortened: {} -> {} (snowflakeId: {})",
            command.originalUrl(), shortUrl.getShortCode(), generated.snowflakeId());

        try {
            UrlCreatedEvent event = UrlCreatedEvent.of(
                generated.snowflakeId(),
                shortUrl.getShortCode(),
                shortUrl.getOriginalUrl()
            );
            redisTemplate.convertAndSend(TopicType.URL_CREATED.getTopicName(), event);
        } catch (Exception e) {
            log.warn("Failed to publish URL created event: {}", e.getMessage());
        }

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
