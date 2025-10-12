package com.io.bitly.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class UrlClickService {

    private static final String CLICK_COUNT_PREFIX = "url:click:";

    private final RedisTemplate<String, String> redisTemplate;

    public void incrementClickCount(Long urlId) {
        String key = CLICK_COUNT_PREFIX + urlId;
        redisTemplate.opsForValue().increment(key);
    }
}
