package com.io.shortly.redirect.infrastructure.redis.cache;

import static com.io.shortly.redirect.infrastructure.redis.cache.CacheLayer.L2;

import com.io.shortly.redirect.domain.Redirect;
import com.io.shortly.redirect.domain.RedirectCache;
import com.io.shortly.redirect.infrastructure.redis.cache.CacheKeyGenerator;
import com.io.shortly.redirect.infrastructure.redis.cache.CachedRedirect;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component("redisCache")
@RequiredArgsConstructor
public class RedirectCacheRedisImpl implements RedirectCache {

    private final RedisTemplate<String, CachedRedirect> redisTemplate;

    @Override
    @CircuitBreaker(name = "redisCache", fallbackMethod = "getFallback")
    public Optional<Redirect> get(String shortCode) {
        String key = CacheKeyGenerator.generateCacheKey(L2, shortCode);
        CachedRedirect cached = redisTemplate.opsForValue().get(key);

        if (cached != null) {
            log.debug("[Cache:L2] 히트: shortCode={}", shortCode);
            return Optional.of(cached.toDomain());
        }

        log.debug("[Cache:L2] 미스: shortCode={}", shortCode);
        return Optional.empty();
    }

    @Override
    @CircuitBreaker(name = "redisCache", fallbackMethod = "putFallback")
    public void put(Redirect redirect) {
        String key = CacheKeyGenerator.generateCacheKey(L2, redirect.getShortCode());
        CachedRedirect cached = CachedRedirect.from(redirect);

        redisTemplate.opsForValue().set(
            key,
            cached,
            L2.getTtl().toMinutes(),
            TimeUnit.MINUTES
        );

        log.debug("[Cache:L2] 저장 완료: shortCode={}", redirect.getShortCode());
    }

    private Optional<Redirect> getFallback(String shortCode, Exception e) {
        log.warn("[Cache:L2] 조회 실패(Circuit Breaker 작동): shortCode={}, error={}",
            shortCode, e.getMessage());
        return Optional.empty();
    }

    private void putFallback(Redirect redirect, Exception e) {
        log.warn("[Cache:L2] 저장 실패(Circuit Breaker 작동): shortCode={}, error={}",
                 redirect.getShortCode(), e.getMessage());
    }
}
