package com.io.shortly.redirect.application;

import com.io.shortly.redirect.application.dto.RedirectResult.RedirectLookupResult;
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
    private final RedirectEventPublisher eventPublisher;

    public RedirectLookupResult getOriginalUrl(String shortCode) {
        Assert.hasText(shortCode, "Short code must not be blank");

        Redirect redirect = redirectCache.get(shortCode)
            .orElseGet(() -> fetchAndCache(shortCode));

        UrlClickedEvent event = UrlClickedEvent.of(
            redirect.getShortCode(),
            redirect.getTargetUrl()
        );
        eventPublisher.publishUrlClicked(event);

        return RedirectLookupResult.of(redirect.getTargetUrl());
    }

    private Redirect fetchAndCache(String shortCode) {
        // URL Service API 호출
        Redirect redirect = urlFetcher.fetchShortUrl(shortCode);

        // L1, L2 캐시 저장 (Adaptive TTL Jitter 적용됨)
        redirectCache.put(redirect);
        return redirect;
    }
}
