package com.io.shortly.infrastructure.cache.redis;

import com.io.shortly.domain.click.ClickService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Profile("!phase5")
@Service
@RequiredArgsConstructor
public class RedisClickService implements ClickService {

    private static final String CLICK_COUNT_PREFIX = "url:click:";

    private final RedisTemplate<String, String> redisTemplate;

    @Override
    public void incrementClickCount(Long urlId) {
        String key = CLICK_COUNT_PREFIX + urlId;
        redisTemplate.opsForValue().increment(key);
    }
}

