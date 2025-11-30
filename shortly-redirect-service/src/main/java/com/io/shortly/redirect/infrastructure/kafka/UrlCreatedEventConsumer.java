package com.io.shortly.redirect.infrastructure.kafka;

import com.io.shortly.redirect.domain.Redirect;
import com.io.shortly.redirect.domain.RedirectCache;
import com.io.shortly.shared.event.UrlCreatedEvent;
import com.io.shortly.shared.kafka.KafkaTopics;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UrlCreatedEventConsumer {

    private final RedirectCache redirectCache;
    private final MeterRegistry meterRegistry;

    @KafkaListener(topics = KafkaTopics.URL_CREATED, groupId = "${spring.kafka.consumer.group-id}", containerFactory = "kafkaListenerContainerFactory")
    public void consumeUrlCreated(UrlCreatedEvent event) {
        String shortCode = event.getShortCode();

        try {
            Redirect redirect = Redirect.create(shortCode, event.getOriginalUrl());

            // L1 + L2 캐시 저장
            redirectCache.put(redirect);

            meterRegistry.counter("url_created_event.processed.success").increment();
        } catch (RedisConnectionFailureException | RedisSystemException e) {
            // Redis 장애
            meterRegistry.counter(
                    "url_created_event.processed.redis_failure",
                    "shortCode", shortCode).increment();
        } catch (IllegalArgumentException | NullPointerException e) {
            // 파라미터 유효성 오류
            meterRegistry.counter("url_created_event.processed.unexpected_error",
                    "shortCode", shortCode,
                    "exception", e.getClass().getSimpleName()).increment();
        } catch (Exception e) {
            meterRegistry.counter("url_created_event.processed.unknown_error",
                    "shortCode", shortCode,
                    "exception", e.getClass().getSimpleName()).increment();
        }
    }
}
