package com.io.shortly.redirect.infrastructure.event.redis;

import com.io.shortly.redirect.domain.Redirect;
import com.io.shortly.redirect.domain.RedirectCacheService;
import com.io.shortly.shared.event.UrlCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class UrlCreatedEventListener implements MessageListener {

    private final RedirectCacheService cacheService;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            UrlCreatedEvent event = objectMapper.readValue(
                message.getBody(),
                UrlCreatedEvent.class
            );

            // L1 + L2 캐시 적재
            Redirect redirect = Redirect.create(
                event.getEventId(),
                event.getShortCode(),
                event.getOriginalUrl()
            );

            cacheService.put(redirect);
        } catch (Exception e) {
            log.warn("캐시 warming 실패: {}", e.getMessage());
        }
    }
}
