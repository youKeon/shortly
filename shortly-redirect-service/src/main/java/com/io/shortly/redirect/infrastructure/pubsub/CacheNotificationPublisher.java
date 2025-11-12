package com.io.shortly.redirect.infrastructure.pubsub;

import static com.io.shortly.redirect.infrastructure.cache.redis.RedisPubSubConfig.CACHE_NOTIFICATION_CHANNEL;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
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
