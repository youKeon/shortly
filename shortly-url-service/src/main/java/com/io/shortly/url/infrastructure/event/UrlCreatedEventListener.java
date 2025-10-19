package com.io.shortly.url.infrastructure.event;

import com.io.shortly.shared.event.UrlCreatedEvent;
import com.io.shortly.url.domain.event.UrlEventPublisher;
import com.io.shortly.url.domain.event.UrlCreatedDomainEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class UrlCreatedEventListener {

    private final UrlEventPublisher eventPublisher;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleUrlCreated(UrlCreatedDomainEvent event) {
        log.debug("Publishing UrlCreatedEvent for shortCode: {}", event.shortCode());

        eventPublisher.publishUrlCreated(
            UrlCreatedEvent.of(event.shortCode(), event.originalUrl())
        );

        log.info("UrlCreatedEvent published for shortCode: {}", event.shortCode());
    }
}
