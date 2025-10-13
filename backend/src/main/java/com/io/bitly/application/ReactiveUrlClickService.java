package com.io.bitly.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
@Profile("phase4")
public class ReactiveUrlClickService {

    private static final String CLICK_COUNT_PREFIX = "url:click:";

    private final ReactiveStringRedisTemplate reactiveStringRedisTemplate;

    public Mono<Long> incrementClickCount(Long urlId) {
        String key = CLICK_COUNT_PREFIX + urlId;

        return reactiveStringRedisTemplate.opsForValue().increment(key)
                .doOnSuccess(count -> log.debug("Click count incremented for URL {}: {}", urlId, count))
                .doOnError(error -> log.error("Failed to increment click count for URL {}: {}",
                        urlId, error.getMessage()))
                .onErrorResume(error -> Mono.empty());
    }
}

