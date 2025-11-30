package com.io.shortly.redirect.application;

import com.io.shortly.redirect.application.dto.RedirectResult.RedirectLookupResult;
import com.io.shortly.redirect.domain.Redirect;
import com.io.shortly.redirect.domain.RedirectCache;
import com.io.shortly.redirect.domain.RedirectEventPublisher;
import com.io.shortly.redirect.domain.UrlFetcher;
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

        // Cache-Aside with Mutex (Atomic Loading)
        // L1(Caffeine)의 get(key, loader)를 사용하여 동시 요청 시 하나만 실행됨
        Redirect redirect = redirectCache.get(shortCode, this::fetchFromUrlService);

        if (redirect == null) {
            // Should not happen if UrlFetcher throws exception on 404
            throw new IllegalStateException("Redirect not found for: " + shortCode);
        }

        UrlClickedEvent event = UrlClickedEvent.of(
                redirect.getShortCode(),
                redirect.getTargetUrl());
        eventPublisher.publishUrlClicked(event);

        return RedirectLookupResult.of(redirect.getTargetUrl());
    }

    private Redirect fetchFromUrlService(String shortCode) {
        // URL Service API 호출 (Loader)
        return urlFetcher.fetchShortUrl(shortCode);
    }
}
