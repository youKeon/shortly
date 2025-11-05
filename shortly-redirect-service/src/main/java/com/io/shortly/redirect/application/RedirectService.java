package com.io.shortly.redirect.application;

import static com.io.shortly.redirect.application.dto.RedirectResult.Redirect;

import com.io.shortly.redirect.domain.RedirectCache;
import com.io.shortly.redirect.domain.RedirectEventPublisher;
import com.io.shortly.redirect.domain.RedirectRepository;
import com.io.shortly.redirect.domain.ShortCodeNotFoundException;
import com.io.shortly.shared.event.UrlClickedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedirectService {

    private final RedirectCache redirectCache;
    private final RedirectRepository redirectRepository;
    private final RedirectEventPublisher eventPublisher;

    public Redirect getOriginalUrl(String shortCode) {
        // 1. 캐시 조회 (L1 → L2)
        return redirectCache.get(shortCode)
            .or(() -> {
                // 2. DB 조회
                log.info("[Service] 캐시 미스: shortCode={}, DB 조회 중", shortCode);

                return redirectRepository.findByShortCode(shortCode)
                    .map(redirect -> {
                        // 3. 캐시 워밍업
                        redirectCache.put(redirect);
                        log.info("[Service] 캐시 워밍업 완료: shortCode={}", shortCode);
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

                // 5. 결과 반환
                return Redirect.of(redirect.getTargetUrl());
            })
            .orElseThrow(() -> {
                log.error("[Service] 리디렉션 실패: shortCode={} 찾을 수 없음", shortCode);
                return new ShortCodeNotFoundException(shortCode);
            });
    }
}
