package com.io.shortly.redirect.infrastructure.cache.pubsub;

import com.github.benmanes.caffeine.cache.Cache;
import com.io.shortly.redirect.domain.Redirect;
import com.io.shortly.redirect.domain.RedirectCache;
import com.io.shortly.redirect.domain.RedirectRepository;
import com.io.shortly.redirect.infrastructure.cache.CacheLayer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(
    prefix = "shortly.cache.sync",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class CacheNotificationListener {

    private final Cache<String, Redirect> caffeineCache;
    private final RedirectCache redisCache;
    private final RedirectRepository redirectRepository;

    public CacheNotificationListener(
            Cache<String, Redirect> caffeineCache,
            @Qualifier("redisCache") RedirectCache redisCache,
            RedirectRepository redirectRepository
    ) {
        this.caffeineCache = caffeineCache;
        this.redisCache = redisCache;
        this.redirectRepository = redirectRepository;
    }

    public void handleUrlCreated(String shortCode) {
        try {
            // L2 (Redis)에서 조회
            redisCache.get(shortCode).ifPresentOrElse(
                redirect -> {
                    // L1 캐시에 저장
                    String l1Key = CacheLayer.L1.buildKey(shortCode);
                    caffeineCache.put(l1Key, redirect);
                },
                () -> {
                    // L2 Miss → DB 조회
                    log.warn("[Cache:L2] Pub/Sub 처리 중 L2 미스, DB로 폴백: shortCode={}", shortCode);
                    redirectRepository.findByShortCode(shortCode).ifPresentOrElse(
                        redirect -> {
                            // DB에서 찾음 → L1/L2 캐시에 모두 저장
                            String l1Key = CacheLayer.L1.buildKey(shortCode);
                            caffeineCache.put(l1Key, redirect);
                            redisCache.put(redirect);  // L2도 함께 저장
                        },
                        () -> {
                            log.error("[Cache:DB] DB에 존재하지 않는 URL입니다: shortCode={}", shortCode);
                        }
                    );
                }
            );

        } catch (Exception e) {
            log.error("[Cache:L1] 알림 처리 실패: shortCode={}", shortCode, e);
        }
    }
}
