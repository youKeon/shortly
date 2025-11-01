package com.io.shortly.redirect.mvc.infrastructure.cache.redis;

import com.io.shortly.redirect.mvc.domain.Redirect;
import com.io.shortly.redirect.mvc.domain.RedirectCache;
import com.io.shortly.redirect.mvc.infrastructure.cache.CachedRedirect;
import com.io.shortly.redirect.mvc.infrastructure.cache.CacheLayer;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component("mvcRedisCache")
@RequiredArgsConstructor
public class RedirectCacheRedisImpl implements RedirectCache {

    private static final CacheLayer LAYER = CacheLayer.L2;

    private final RedisTemplate<String, CachedRedirect> redisTemplate;

    @Override
    public Optional<Redirect> get(String shortCode) {
        try {
            String key = LAYER.buildKey(shortCode);
            CachedRedirect cached = redisTemplate.opsForValue().get(key);

            if (cached != null) {
                log.debug("[Cache:L2-MVC] Hit: shortCode={}", shortCode);
                return Optional.of(cached.toDomain());
            }

            log.debug("[Cache:L2-MVC] Miss: shortCode={}", shortCode);
            return Optional.empty();
        } catch (Exception e) {
            log.warn("[Cache:L2-MVC] Read failed: shortCode={}, continuing", shortCode, e);
            return Optional.empty();
        }
    }

    @Override
    public void put(Redirect redirect) {
        try {
            String key = LAYER.buildKey(redirect.getShortCode());
            CachedRedirect cached = CachedRedirect.from(redirect);

            redisTemplate.opsForValue().set(
                key,
                cached,
                LAYER.getTtl().toMinutes(),
                TimeUnit.MINUTES
            );

            log.debug("[Cache:L2-MVC] Put: shortCode={}", redirect.getShortCode());
        } catch (Exception e) {
            log.warn("[Cache:L2-MVC] Put failed: shortCode={}", redirect.getShortCode(), e);
            throw e;
        }
    }

    @Override
    public void evict(String shortCode) {
        try {
            String key = LAYER.buildKey(shortCode);
            redisTemplate.delete(key);
            log.debug("[Cache:L2-MVC] Evicted: shortCode={}", shortCode);
        } catch (Exception e) {
            log.warn("[Cache:L2-MVC] Eviction failed: shortCode={}", shortCode, e);
            throw e;
        }
    }
}
