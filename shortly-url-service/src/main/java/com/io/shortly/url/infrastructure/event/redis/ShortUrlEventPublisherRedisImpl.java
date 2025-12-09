package com.io.shortly.url.infrastructure.event.redis;

import com.io.shortly.shared.event.UrlCreatedEvent;
import com.io.shortly.shared.kafka.TopicType;
import com.io.shortly.url.domain.ShortUrlEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ShortUrlEventPublisherRedisImpl implements ShortUrlEventPublisher {

    private final RedisTemplate<String, UrlCreatedEvent> redisTemplate;

    @Override
    public void publishUrlCreated(UrlCreatedEvent event) {
        try {
            redisTemplate.convertAndSend(TopicType.URL_CREATED.getTopicName(), event);
            log.debug("[Event] URL 단축 이벤트 발행 성공: eventId={}, shortCode={}",
                event.getEventId(), event.getShortCode()
            );
        } catch (Exception e) {
            log.error("[Event] URL 단축 이벤트 발행 실패 - eventId={}, shortCode={}, error={}",
                event.getEventId(), event.getShortCode(), e.getMessage()
            );
        }
    }
}
