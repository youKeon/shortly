package com.io.shortly.redirect.infrastructure.cache.pubsub;

import static com.io.shortly.redirect.infrastructure.cache.redis.RedisPubSubConfig.CACHE_NOTIFICATION_CHANNEL;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "shortly.cache.sync",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class CacheNotificationPublisher {

    private final StringRedisTemplate stringRedisTemplate;

    public void notifyUrlCreated(String shortCode) {
        try {
            stringRedisTemplate.convertAndSend(CACHE_NOTIFICATION_CHANNEL, shortCode);
            log.debug("[Cache:Notification] URL 생성 알림 발행 완료: shortCode={}", shortCode);

        } catch (Exception e) {
            log.error("[Cache:Notification] 알림 발행 실패: shortCode={}", shortCode, e);
        }
    }
}
