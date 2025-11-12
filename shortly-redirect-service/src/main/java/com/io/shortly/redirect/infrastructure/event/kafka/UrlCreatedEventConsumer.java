package com.io.shortly.redirect.infrastructure.event.kafka;

import com.io.shortly.redirect.domain.Redirect;
import com.io.shortly.redirect.domain.RedirectCache;
import com.io.shortly.redirect.infrastructure.pubsub.CacheNotificationPublisher;
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

    private final RedirectCache redirectCache;
    private final CacheNotificationPublisher cacheNotificationPublisher;

    @KafkaListener(
        topics = KafkaTopics.URL_CREATED,
        groupId = KafkaTopics.REDIRECT_SERVICE_GROUP,
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeUrlCreated(UrlCreatedEvent event) {
        log.debug("[Event] 이벤트 수신: shortCode={}, topic={}", event.getShortCode(), KafkaTopics.URL_CREATED);

        try {
            Redirect redirect = Redirect.create(
                event.getShortCode(),
                event.getOriginalUrl()
            );

            // 캐시 저장
            redirectCache.put(redirect);

            // L1 캐시 동기화 (Redis Pub/Sub)
            cacheNotificationPublisher.notifyUrlCreated(redirect.getShortCode());

            log.info("[Event] 캐시 저장 완료: shortCode={}", redirect.getShortCode());

        } catch (Exception e) {
            log.error("[Event] 처리 실패, 재시도 예정: shortCode={}", event.getShortCode(), e);
        }
    }
}
