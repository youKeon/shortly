package com.io.shortly.infrastructure.cache.redis;

import com.io.shortly.domain.click.ReactiveUrlClickService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Slf4j
@Profile("phase5")
@Service
@RequiredArgsConstructor
public class ReactiveRedisUrlClickService implements ReactiveUrlClickService {

    private static final String CLICK_COUNT_PREFIX = "click:count:";

    private final ReactiveRedisTemplate<String, String> reactiveStringRedisTemplate;

    @Override
    public Mono<Void> incrementClickCount(Long urlId) {
        String key = CLICK_COUNT_PREFIX + urlId;
        return reactiveStringRedisTemplate.opsForValue()
            .increment(key)
            .then();
    }
}

