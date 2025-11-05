package com.io.shortly.redirect.application;

import static com.io.shortly.redirect.application.dto.RedirectResult.Redirect;

import com.io.shortly.redirect.domain.RedirectCache;
import com.io.shortly.redirect.domain.RedirectEventPublisher;
import com.io.shortly.redirect.domain.ShortCodeNotFoundException;
import com.io.shortly.redirect.infrastructure.client.UrlServiceClient;
import com.io.shortly.shared.event.UrlClickedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedirectService {

    private final RedirectCache redirectCache;
    private final UrlServiceClient urlServiceClient;
    private final RedirectEventPublisher eventPublisher;

    public Redirect getOriginalUrl(String shortCode) {
        // 1. 캐시 조회 (L1 Caffeine → L2 Redis)
        return redirectCache.get(shortCode)
            .or(() -> {
                // 2. 캐시 미스 → URL Service API 호출
                log.warn("[Cache Miss] shortCode={}, URL Service API 호출", shortCode);

                return urlServiceClient.findByShortCode(shortCode)
                    .map(redirect -> {
                        // 3. 캐시 워밍업
                        redirectCache.put(redirect);
                        log.info("[Cache Warmup] API로 조회한 데이터 캐싱: shortCode={}",
                                shortCode);
                        return redirect;
                    });
            })
            .map(redirect -> {
                // 4. 클릭 이벤트 발행
                UrlClickedEvent event = UrlClickedEvent.of(
                    redirect.getShortCode(),
                    redirect.getTargetUrl()
                );
                eventPublisher.publishUrlClicked(event);

                return Redirect.of(redirect.getTargetUrl());
            })
            .orElseThrow(() -> {
                log.error("[Not Found] shortCode={} 찾을 수 없음", shortCode);
                return new ShortCodeNotFoundException(shortCode);
            });
    }
}
