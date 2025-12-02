package com.io.shortly.redirect.infrastructure.cache.redis;

import static com.io.shortly.redirect.infrastructure.cache.CacheLayer.L2;

import com.io.shortly.redirect.domain.Redirect;
import com.io.shortly.redirect.domain.RedirectCache;
import com.io.shortly.redirect.infrastructure.cache.CacheKeyGenerator;
import com.io.shortly.redirect.infrastructure.cache.CachedRedirect;
import java.time.Duration;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component("redisCache")
public class RedirectCacheRedisImpl implements RedirectCache {

    private final RedisTemplate<String, CachedRedirect> redisTemplate;
    private final Duration l2Ttl;

    public RedirectCacheRedisImpl(
            RedisTemplate<String, CachedRedirect> redisTemplate,
            @Value("${shortly.cache.l2.ttl:30m}") Duration l2Ttl
    ) {
        this.redisTemplate = redisTemplate;
        this.l2Ttl = l2Ttl;
    }

    @Override
    public Optional<Redirect> get(String shortCode) {
        try {
            String key = CacheKeyGenerator.generateCacheKey(L2, shortCode);
            CachedRedirect cached = redisTemplate.opsForValue().get(key);

            if (cached != null) {
                log.info("[Cache:L2] HIT - shortCode={}, targetUrl={}", shortCode, cached.targetUrl());
                return Optional.of(cached.toDomain());
            }

            log.info("[Cache:L2] MISS - shortCode={}", shortCode);
            return Optional.empty();

        } catch (Exception e) {
            log.warn("[Cache:L2] 조회 실패: shortCode={}, error={}",
                    shortCode, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void put(Redirect redirect) {
        try {
            String key = CacheKeyGenerator.generateCacheKey(L2, redirect.getShortCode());
            CachedRedirect cached = CachedRedirect.from(redirect);

            redisTemplate.opsForValue().set(key, cached, l2Ttl);

            log.debug("[Cache:L2] 저장 완료: shortCode={}, ttl={}",
                    redirect.getShortCode(), l2Ttl);

        } catch (Exception e) {
            log.warn("[Cache:L2] 저장 실패: shortCode={}, error={}",
                    redirect.getShortCode(), e.getMessage());
        }
    }
}
