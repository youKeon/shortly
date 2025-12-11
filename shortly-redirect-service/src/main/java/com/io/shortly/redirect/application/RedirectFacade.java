package com.io.shortly.redirect.application;

import com.io.shortly.redirect.application.dto.RedirectResult.RedirectLookupResult;
import com.io.shortly.redirect.domain.Redirect;
import com.io.shortly.redirect.domain.RedirectCacheService;
import com.io.shortly.redirect.domain.RedirectEventPublisher;
import com.io.shortly.shared.event.UrlClickedEvent;
import com.io.shortly.shared.id.UniqueIdGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedirectFacade {

    private final RedirectCacheService cacheService;
    private final RedirectEventPublisher eventPublisher;
    private final UniqueIdGenerator uniqueIdGenerator;

    public RedirectLookupResult getOriginalUrl(String shortCode) {
        Assert.hasText(shortCode, "Short code must not be blank");

        Redirect redirect = cacheService.getRedirect(shortCode);

        long eventId = uniqueIdGenerator.generate();

        UrlClickedEvent event = UrlClickedEvent.of(
                eventId,
                redirect.getShortCode(),
                redirect.getTargetUrl()
        );
        eventPublisher.publishUrlClicked(event);

        return RedirectLookupResult.of(redirect.getTargetUrl());
    }
}
