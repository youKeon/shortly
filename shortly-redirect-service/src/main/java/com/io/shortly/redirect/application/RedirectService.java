package com.io.shortly.redirect.application;

import com.io.shortly.redirect.application.dto.RedirectResult.RedirectLookupResult;
import com.io.shortly.redirect.domain.DistributedLockTemplate;
import com.io.shortly.redirect.domain.Redirect;
import com.io.shortly.redirect.domain.RedirectCache;
import com.io.shortly.redirect.domain.RedirectEventPublisher;
import com.io.shortly.redirect.domain.ShortCodeNotFoundException;
import com.io.shortly.redirect.infrastructure.client.UrlLookupResponse;
import com.io.shortly.shared.event.UrlClickedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedirectService {

    private final RestClient urlServiceRestClient;
    private final RedirectCache redirectCache;
    private final DistributedLockTemplate lockTemplate;
    private final RedirectEventPublisher eventPublisher;

    private static final String LOCK_KEY_PREFIX = "cache:lock:";
    private static final String GET_SHORT_CODE_URI = "/api/v1/urls/{shortCode}";

    public RedirectLookupResult getOriginalUrl(String shortCode) {
        Redirect redirect = redirectCache.get(shortCode)
            .orElseGet(() -> fetchWithDistributedLock(shortCode));

        UrlClickedEvent event = UrlClickedEvent.of(
            redirect.getShortCode(),
            redirect.getTargetUrl()
        );
        eventPublisher.publishUrlClicked(event);

        return RedirectLookupResult.of(redirect.getTargetUrl());
    }

    private Redirect fetchWithDistributedLock(String shortCode) {
        String lockKey = LOCK_KEY_PREFIX + shortCode;

        return lockTemplate.executeWithLock(lockKey, () -> {

            // 2차 캐시 확인 (Double-check)
            return redirectCache.get(shortCode)
                .orElseGet(() -> {

                    // 캐시 미스 시 URL Service API 호출
                    Redirect redirect = fetchFromUrlService(shortCode);

                    // L1, L2 캐시 워밍업
                    redirectCache.put(redirect);

                    return redirect;
                });
        });
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
                throw new ShortCodeNotFoundException(shortCode);
            }

            log.info("[API Fallback] 조회 성공: shortCode={}, url={}",
                response.shortCode(), response.originalUrl());

            return Redirect.create(response.shortCode(), response.originalUrl());

        } catch (RestClientException e) {
            log.error("[API Fallback] 호출 실패: shortCode={}, error={}",
                shortCode, e.getMessage());
            throw new ShortCodeNotFoundException(shortCode);
        } catch (Exception e) {
            log.error("[API Fallback] 예상치 못한 오류: shortCode={}", shortCode, e);
            throw new ShortCodeNotFoundException(shortCode);
        }
    }
}
