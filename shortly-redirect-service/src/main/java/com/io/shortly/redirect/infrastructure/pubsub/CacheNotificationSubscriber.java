package com.io.shortly.redirect.infrastructure.pubsub;

import com.github.benmanes.caffeine.cache.Cache;
import com.io.shortly.redirect.domain.Redirect;
import com.io.shortly.redirect.domain.RedirectCache;
import com.io.shortly.redirect.infrastructure.cache.CacheKeyGenerator;
import com.io.shortly.redirect.infrastructure.cache.CacheLayer;
import com.io.shortly.redirect.infrastructure.client.UrlLookupResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Slf4j
@Component
@RequiredArgsConstructor
public class CacheNotificationSubscriber {

    private final Cache<String, Redirect> caffeineCache;

    @Qualifier("redisCache")
    private final RedirectCache redisCache;

    private final RestClient urlServiceRestClient;

    private static final String GET_SHORT_CODE_URI = "/api/v1/urls/{shortCode}";

    public void onUrlCreated(String shortCode) {
        try {
            // L2 (Redis)에서 단축 코드를 조회해서 L1 (Caffeine)에 저장
            redisCache.get(shortCode).ifPresentOrElse(
                redirect -> {
                    String l1Key = CacheKeyGenerator.generateCacheKey(CacheLayer.L1, shortCode);
                    caffeineCache.put(l1Key, redirect);
                },
                () -> {
                    // L2 Miss → URL Service API 호출
                    log.warn("[Cache:L2] Pub/Sub 처리 중 L2 미스, API로 폴백: shortCode={}", shortCode);
                    try {
                        Redirect redirect = fetchFromUrlService(shortCode);
                        // L1/L2 캐시에 모두 저장
                        String l1Key = CacheKeyGenerator.generateCacheKey(CacheLayer.L1, shortCode);
                        caffeineCache.put(l1Key, redirect);
                        redisCache.put(redirect);
                    } catch (Exception e) {
                        log.error("[Cache:API] URL Service 호출 실패: shortCode={}", shortCode, e);
                    }
                }
            );

        } catch (Exception e) {
            log.error("[Cache:L1] 알림 처리 실패: shortCode={}", shortCode, e);
        }
    }

    private Redirect fetchFromUrlService(String shortCode) {
        log.info("[API Fallback] URL Service 호출 시작: shortCode={}", shortCode);

        try {
            UrlLookupResponse response = urlServiceRestClient.get()
                .uri(GET_SHORT_CODE_URI, shortCode)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (request, responseEntity) ->
                    log.warn("[API Fallback] 4xx 에러: status={}, shortCode={}",
                        responseEntity.getStatusCode(), shortCode)
                )
                .onStatus(HttpStatusCode::is5xxServerError, (request, responseEntity) ->
                    log.error("[API Fallback] 5xx 에러: status={}, shortCode={}",
                        responseEntity.getStatusCode(), shortCode)
                )
                .body(UrlLookupResponse.class);

            if (response == null) {
                log.warn("[API Fallback] 응답이 null: shortCode={}", shortCode);
                throw new RuntimeException("URL Service 응답이 null입니다");
            }

            log.info("[API Fallback] 조회 성공: shortCode={}, url={}",
                response.shortCode(), response.originalUrl());

            return Redirect.create(response.shortCode(), response.originalUrl());

        } catch (RestClientException e) {
            log.error("[API Fallback] 호출 실패: shortCode={}, error={}",
                shortCode, e.getMessage());
            throw new RuntimeException("URL Service 호출 실패", e);
        }
    }
}
