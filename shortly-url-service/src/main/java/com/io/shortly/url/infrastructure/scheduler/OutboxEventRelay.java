package com.io.shortly.url.infrastructure.scheduler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.io.shortly.shared.event.UrlCreatedEvent;
import com.io.shortly.shared.kafka.KafkaTopics;
import com.io.shortly.url.infrastructure.persistence.jpa.outbox.OutboxJpaEntity;
import com.io.shortly.url.infrastructure.persistence.jpa.outbox.OutboxJpaRepository;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxEventRelay {

    private static final int BATCH_SIZE = 100;
    private static final int ONE_SECOND = 1000;
    private static final int ONE_MINUTE = 60000;
    private static final int STUCK_EVENT_THRESHOLD_MINUTES = 5;

    private final OutboxJpaRepository outboxJpaRepository;
    private final KafkaTemplate<String, UrlCreatedEvent> kafkaTemplate;
    private final ObjectMapper objectMapper;

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    @Scheduled(fixedDelay = ONE_SECOND)
    public void relayPendingEvents() {
        List<OutboxJpaEntity> events = outboxJpaRepository.findPendingEvents(
                PageRequest.of(0, BATCH_SIZE)
        );

        if (events.isEmpty()) {
            return;
        }

        events.forEach(OutboxJpaEntity::markAsProcessing);
        outboxJpaRepository.saveAll(events);
        outboxJpaRepository.flush();

        events.forEach(event -> {
            executor.submit(() -> {
                try {
                    UrlCreatedEvent urlCreatedEvent = objectMapper.readValue(
                            event.getPayload(),
                            UrlCreatedEvent.class
                    );

                    kafkaTemplate.send(
                            KafkaTopics.URL_CREATED,
                            event.getAggregateId(),
                            urlCreatedEvent).whenComplete((result, ex) -> {
                                if (ex == null) {
                                    event.markAsPublished();
                                    outboxJpaRepository.save(event);
                                } else {
                                    event.markAsPending();
                                    outboxJpaRepository.save(event);
                                }
                            });

                } catch (Exception e) {
                    event.markAsPending();
                    outboxJpaRepository.save(event);
                }
            });
        });
    }

    @Transactional
    @Scheduled(fixedDelay = ONE_MINUTE)
    public void recoverStuckEvents() {
        Instant threshold = Instant.now().minusSeconds(STUCK_EVENT_THRESHOLD_MINUTES * 60);
        List<OutboxJpaEntity> stuckEvents = outboxJpaRepository.findStuckEvents(threshold);

        if (stuckEvents.isEmpty()) {
            return;
        }

        stuckEvents.forEach(OutboxJpaEntity::markAsPending);
        outboxJpaRepository.saveAll(stuckEvents);
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
