package com.io.shortly.redirect.webflux.application;

import static com.io.shortly.redirect.webflux.application.dto.RedirectResult.Redirect;

import com.io.shortly.redirect.webflux.domain.RedirectCache;
import com.io.shortly.redirect.webflux.domain.RedirectEventPublisher;
import com.io.shortly.redirect.webflux.domain.RedirectRepository;
import com.io.shortly.redirect.webflux.domain.ShortCodeNotFoundException;
import com.io.shortly.shared.event.UrlClickedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedirectService {

    private final RedirectCache redirectCache;
    private final RedirectRepository redirectRepository;
    private final RedirectEventPublisher eventPublisher;

    public Mono<Redirect> getOriginalUrl(String shortCode) {
        return redirectCache.get(shortCode)
            .switchIfEmpty(
                Mono.defer(() -> {
                    log.info("[Service] Cache miss: shortCode={}, querying DB", shortCode);

                    return redirectRepository.findByShortCode(shortCode)
                        .flatMap(redirect ->
                            redirectCache.put(redirect)
                                .doOnSuccess(_void -> log.info("[Service] Cache warmed: shortCode={}", shortCode))
                                .thenReturn(redirect)
                        );
                })
            )
            .switchIfEmpty(
                Mono.error(new ShortCodeNotFoundException(shortCode)))
            .doOnNext(redirect -> {
                UrlClickedEvent event = UrlClickedEvent.of(
                    redirect.getShortCode(),
                    redirect.getTargetUrl()
                );
                eventPublisher.publishUrlClicked(event);
            })
            .map(redirect -> Redirect.of(redirect.getTargetUrl()))
            .doOnError(error -> log.error("[Service] Redirect failed: shortCode={}", shortCode, error));
    }
}
