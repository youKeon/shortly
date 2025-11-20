package com.io.shortly.redirect.infrastructure.redis.pubsub;

import com.github.benmanes.caffeine.cache.Cache;
import com.io.shortly.redirect.domain.Redirect;
import com.io.shortly.redirect.domain.RedirectCache;
import com.io.shortly.redirect.infrastructure.client.UrlServiceClient;
import com.io.shortly.redirect.infrastructure.redis.cache.CacheKeyGenerator;
import com.io.shortly.redirect.infrastructure.redis.cache.CacheLayer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CacheNotificationSubscriber {

    private final Cache<String, Redirect> caffeineCache;

    @Qualifier("redisCache")
    private final RedirectCache redisCache;

    private final UrlServiceClient urlServiceClient;

    public void onUrlCreated(String shortCode) {
        try {
            // L2 (Redis)에서 단축 코드를 조회해서 L1 (Caffeine)에 저장
            redisCache.get(shortCode).ifPresentOrElse(
                redirect -> {
                    String l1Key = CacheKeyGenerator.generateCacheKey(CacheLayer.L1, shortCode);
                    caffeineCache.put(l1Key, redirect);
                    log.debug("[Cache:L1] L2→L1 동기화 완료: shortCode={}", shortCode);
                },
                () -> {
                    // L2 Miss → URL Service API 호출
                    log.warn("[Cache:L2] Pub/Sub 처리 중 L2 미스, API로 폴백: shortCode={}", shortCode);
                    try {
                        Redirect redirect = urlServiceClient.fetchShortUrl(shortCode);
                        // L1/L2 캐시에 모두 저장
                        String l1Key = CacheKeyGenerator.generateCacheKey(CacheLayer.L1, shortCode);
                        caffeineCache.put(l1Key, redirect);
                        redisCache.put(redirect);
                        log.debug("[Cache:API] API→L1/L2 저장 완료: shortCode={}", shortCode);
                    } catch (Exception e) {
                        log.error("[Cache:API] URL Service 호출 실패: shortCode={}", shortCode, e);
                    }
                }
            );

        } catch (Exception e) {
            log.error("[Cache:L1] 알림 처리 실패: shortCode={}", shortCode, e);
        }
    }
}
