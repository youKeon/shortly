package com.io.shortly.redirect.infrastructure.redis.pubsub;

import static com.io.shortly.redirect.infrastructure.redis.config.RedisPubSubConfig.CACHE_NOTIFICATION_CHANNEL;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CacheNotificationPublisher {

    private final StringRedisTemplate stringRedisTemplate;
    private final MeterRegistry meterRegistry;

    public void notifyUrlCreated(String shortCode) {
        try {
            stringRedisTemplate.convertAndSend(CACHE_NOTIFICATION_CHANNEL, shortCode);
            meterRegistry.counter("cache.pubsub.published.success").increment();
            log.debug("[Cache:Notification] Pub/Sub 발행 완료: shortCode={}", shortCode);

        } catch (Exception e) {
            meterRegistry.counter("cache.pubsub.published.failure").increment();
            log.warn("[Cache:Notification] Pub/Sub 발행 실패: shortCode={}", shortCode, e);
        }
    }
}
