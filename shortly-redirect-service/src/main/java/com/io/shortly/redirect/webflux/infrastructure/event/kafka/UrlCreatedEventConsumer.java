package com.io.shortly.redirect.webflux.infrastructure.event.kafka;

import com.io.shortly.shared.event.UrlCreatedEvent;
import com.io.shortly.shared.kafka.KafkaTopics;
import com.io.shortly.redirect.webflux.domain.Redirect;
import com.io.shortly.redirect.webflux.domain.RedirectCache;
import com.io.shortly.redirect.webflux.domain.RedirectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class UrlCreatedEventConsumer {

    private final RedirectCache redirectCache;
    private final RedirectRepository redirectRepository;

    @KafkaListener(
        topics = KafkaTopics.URL_CREATED,
        groupId = KafkaTopics.REDIRECT_SERVICE_GROUP,
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeUrlCreated(UrlCreatedEvent event) {
        log.debug("[Event] Received: shortCode={}, topic={}", event.getShortCode(), KafkaTopics.URL_CREATED);

        Redirect redirect = Redirect.create(
            event.getShortCode(),
            event.getOriginalUrl()
        );

        Mono.defer(() -> redirectRepository.save(redirect)
                .flatMap(saved -> redirectCache.put(saved).thenReturn(saved))
            )
            .onErrorResume(error -> {
                log.error("[Event] Synchronization failed: shortCode={}, topic={}",
                    event.getShortCode(), KafkaTopics.URL_CREATED, error);
                // TODO: Dead Letter Queue
                return Mono.empty();
            })
            .subscribe();
    }
}
