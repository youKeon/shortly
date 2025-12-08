package com.io.shortly.redirect.infrastructure.queue.kafka;

import com.io.shortly.redirect.domain.Redirect;
import com.io.shortly.redirect.domain.RedirectCacheService;
import com.io.shortly.shared.event.UrlCreatedEvent;
import com.io.shortly.shared.kafka.KafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class UrlCreatedEventConsumer {

    private final RedirectCacheService cacheService;

    @KafkaListener(topics = KafkaTopics.URL_CREATED, groupId = "${spring.kafka.consumer.group-id}", containerFactory = "kafkaListenerContainerFactory")
    public void consumeUrlCreated(UrlCreatedEvent event) {
        try {
            Redirect redirect = Redirect.create(event.getShortCode(), event.getOriginalUrl());

            // L1 + L2 캐시 저장
            cacheService.put(redirect);
        } catch (Exception e) {
            log.error("캐시 저장 중 오류 발생");
        }
    }
}
