package com.io.shortly.redirect.infrastructure.kafka;

import com.io.shortly.redirect.domain.Redirect;
import com.io.shortly.redirect.domain.RedirectCache;
import com.io.shortly.redirect.infrastructure.redis.pubsub.CacheNotificationPublisher;
import com.io.shortly.shared.event.UrlCreatedEvent;
import com.io.shortly.shared.kafka.KafkaTopics;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class UrlCreatedEventConsumer {

    private final RedirectCache redirectCache;
    private final CacheNotificationPublisher cacheNotificationPublisher;
    private final MeterRegistry meterRegistry;

    @KafkaListener(
        topics = KafkaTopics.URL_CREATED,
        groupId = "${spring.kafka.consumer.group-id}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeUrlCreated(UrlCreatedEvent event) {
        String shortCode = event.getShortCode();
        log.debug("[Event] 이벤트 수신: shortCode={}, topic={}", shortCode, KafkaTopics.URL_CREATED);

        try {
            Redirect redirect = Redirect.create(shortCode, event.getOriginalUrl());

            // L1 + L2 캐시 저장
            redirectCache.put(redirect);

            // 다른 인스턴스 L1 동기화 (Redis Pub/Sub)
            cacheNotificationPublisher.notifyUrlCreated(shortCode);

            meterRegistry.counter("url_created_event.processed.success").increment();
            log.info("[Event] 캐시 워밍업 완료: shortCode={}", shortCode);

        } catch (RedisConnectionFailureException | RedisSystemException e) {
            // Redis 장애
            meterRegistry.counter("url_created_event.processed.redis_failure",
                "shortCode", shortCode).increment();
            log.warn("[Event] Redis 장애로 캐시 저장 일부 실패 (무시): shortCode={}, error={}",
                shortCode, e.getClass().getSimpleName());

        } catch (DataAccessException e) {
            // Pub/Sub 장애
            meterRegistry.counter("url_created_event.processed.pubsub_failure",
                "shortCode", shortCode).increment();
            log.warn("[Event] Pub/Sub 장애로 L1 동기화 실패 (무시): shortCode={}",
                shortCode);

        } catch (IllegalArgumentException | NullPointerException e) {
            // 파라미터 유효성 오류
            meterRegistry.counter("url_created_event.processed.unexpected_error",
                "shortCode", shortCode,
                "exception", e.getClass().getSimpleName()).increment();
            log.error("[Event] 예상치 못한 오류 (버그 가능성, 코드 리뷰 필요): shortCode={}, event={}",
                shortCode, event, e);

        } catch (Exception e) {
            // 알 수 없는 오류
            meterRegistry.counter("url_created_event.processed.unknown_error",
                "shortCode", shortCode,
                "exception", e.getClass().getSimpleName()).increment();
            log.error("[Event] 알 수 없는 오류 (긴급 점검 필요): shortCode={}, event={}",
                shortCode, event, e);
        }
    }
}
