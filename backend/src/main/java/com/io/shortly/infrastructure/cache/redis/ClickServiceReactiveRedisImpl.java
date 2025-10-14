package com.io.shortly.infrastructure.cache.redis;

import com.io.shortly.domain.click.ClickService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Profile("phase5")
@Service
@RequiredArgsConstructor
public class ClickServiceReactiveRedisImpl implements ClickService {

    private static final String CLICK_COUNT_PREFIX = "click:count:";

    private final ReactiveRedisTemplate<String, String> reactiveStringRedisTemplate;

    @Override
    public void incrementClickCount(Long urlId) {
        if (urlId == null) {
            return;
        }

        String key = CLICK_COUNT_PREFIX + urlId;
        reactiveStringRedisTemplate.opsForValue()
            .increment(key)
            .doOnError(ex -> log.warn("Failed to increment click count in Redis for urlId={}", urlId, ex))
            .block();
    }
}
