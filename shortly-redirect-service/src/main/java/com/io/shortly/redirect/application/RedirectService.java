package com.io.shortly.redirect.application;

import com.io.shortly.redirect.application.dto.RedirectResult.RedirectLookupResult;
import com.io.shortly.redirect.domain.DistributedLockTemplate;
import com.io.shortly.redirect.domain.Redirect;
import com.io.shortly.redirect.domain.RedirectCache;
import com.io.shortly.redirect.domain.RedirectEventPublisher;
import com.io.shortly.shared.event.UrlClickedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedirectService {

    private final UrlFetcher urlFetcher;
    private final RedirectCache redirectCache;
    private final DistributedLockTemplate lockTemplate;
    private final RedirectEventPublisher eventPublisher;

    private static final String LOCK_KEY_PREFIX = "cache:lock:";

    public RedirectLookupResult getOriginalUrl(String shortCode) {
        Assert.hasText(shortCode, "Short code must not be blank");

        Redirect redirect = redirectCache.get(shortCode)
            .orElseGet(() -> fetchWithDistributedLock(shortCode));

        // 클릭 이벤트 발행
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
                    Redirect redirect = urlFetcher.fetchShortUrl(shortCode);

                    // L1, L2 캐시 워밍업
                    redirectCache.put(redirect);

                    return redirect;
                });
        });
    }
}
