package com.io.shortly.redirect.mvc.infrastructure.event.kafka;

import com.io.shortly.redirect.mvc.domain.Redirect;
import com.io.shortly.redirect.mvc.domain.RedirectCache;
import com.io.shortly.redirect.mvc.domain.RedirectRepository;
import com.io.shortly.shared.event.UrlCreatedEvent;
import com.io.shortly.shared.kafka.KafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class UrlCreatedEventConsumer {

    private final RedirectCache redirectCache;
    private final RedirectRepository redirectRepository;

    @KafkaListener(
        topics = KafkaTopics.URL_CREATED,
        groupId = KafkaTopics.REDIRECT_SERVICE_GROUP,
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeUrlCreated(UrlCreatedEvent event, Acknowledgment ack) {
        log.debug("[Event-MVC] Received: shortCode={}, topic={}", event.getShortCode(), KafkaTopics.URL_CREATED);

        try {
            Redirect redirect = Redirect.create(
                event.getShortCode(),
                event.getOriginalUrl()
            );

            Redirect saved = redirectRepository.save(redirect);

            redirectCache.put(saved);

            // 수동 커밋 (성공 시에만)
            ack.acknowledge();
            log.info("[Event-MVC] Processed and committed: shortCode={}", saved.getShortCode());

        } catch (Exception e) {
            log.error("[Event-MVC] Processing failed, will retry: shortCode={}", event.getShortCode(), e);
            // 커밋하지 않음 → Kafka가 재전송
            // TODO: Dead Letter Queue
        }
    }
}
