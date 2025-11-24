package com.io.shortly.redirect.infrastructure.redis.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import com.io.shortly.redirect.domain.Redirect;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CacheConfig {

    private final long l1MaxSize;
    private final Duration l1Ttl;
    private final double jitterMin;
    private final double jitterMax;

    public CacheConfig(
        @Value("${shortly.cache.l1.max-size:100000}") long l1MaxSize,
        @Value("${shortly.cache.l1.ttl:10m}") Duration l1Ttl,
        @Value("${shortly.cache.l1.jitter-min:0.8}") double jitterMin,
        @Value("${shortly.cache.l1.jitter-max:1.2}") double jitterMax
    ) {
        this.l1MaxSize = l1MaxSize;
        this.l1Ttl = l1Ttl;
        this.jitterMin = jitterMin;
        this.jitterMax = jitterMax;
    }

    @Bean
    public Cache<String, Redirect> caffeineCache() {
        return Caffeine.newBuilder()
            .maximumSize(l1MaxSize)
            .expireAfter(new AdaptiveTTLExpiry(l1Ttl, jitterMin, jitterMax))
            .recordStats()
            .build();
    }

    /**
     * Adaptive TTL Expiry with Jitter
     *
     * <p>동시 만료(Thundering Herd)를 방지하기 위해 TTL에 랜덤 Jitter를 추가합니다.
     * 기본 TTL의 ±20% 범위 내에서 랜덤하게 만료 시간을 분산시킵니다.
     *
     * <p>예시: TTL 10분 설정 시 → 실제 만료: 8~12분 사이 랜덤
     */
    private static class AdaptiveTTLExpiry implements Expiry<String, Redirect> {
        private final long baseTtlNanos;
        private final double jitterMin;
        private final double jitterMax;

        AdaptiveTTLExpiry(Duration baseTtl, double jitterMin, double jitterMax) {
            this.baseTtlNanos = baseTtl.toNanos();
            this.jitterMin = jitterMin;
            this.jitterMax = jitterMax;
        }

        @Override
        public long expireAfterCreate(String key, Redirect value, long currentTime) {
            return applyJitter();
        }

        @Override
        public long expireAfterUpdate(String key, Redirect value, long currentTime, long currentDuration) {
            return applyJitter();
        }

        @Override
        public long expireAfterRead(String key, Redirect value, long currentTime, long currentDuration) {
            return currentDuration;
        }

        private long applyJitter() {
            double jitter = jitterMin + (Math.random() * (jitterMax - jitterMin));
            return (long) (baseTtlNanos * jitter);
        }
    }
}
