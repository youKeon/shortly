package com.io.shortly.url.infrastructure.scheduler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.io.shortly.shared.event.UrlCreatedEvent;
import com.io.shortly.shared.kafka.KafkaTopics;
import com.io.shortly.url.infrastructure.persistence.jpa.OutboxEntity;
import com.io.shortly.url.infrastructure.persistence.jpa.OutboxJpaRepository;
import java.util.List;
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
public class OutboxPublisher {

    private static final int BATCH_SIZE = 100;
    private static final int ONE_SECOND = 1000;

    private final OutboxJpaRepository outboxJpaRepository;
    private final KafkaTemplate<String, UrlCreatedEvent> kafkaTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 발행 대기 중인 이벤트를 폴링하여 Kafka로 발행
     */
    @Scheduled(fixedDelay = ONE_SECOND)
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEntity> events = outboxJpaRepository.findPendingEvents(
            PageRequest.of(0, BATCH_SIZE)
        );

        if (events.isEmpty()) {
            return;
        }

        log.info("Publishing {} pending outbox events", events.size());

        for (var event : events) {
            try {
                UrlCreatedEvent urlCreatedEvent = objectMapper.readValue(
                    event.getPayload(),
                    UrlCreatedEvent.class
                );

                kafkaTemplate.send(
                    KafkaTopics.URL_CREATED,
                    event.getAggregateId(),
                    urlCreatedEvent
                ).get();

                event.markAsPublished();
                outboxJpaRepository.save(event);

                log.info("Published outbox event: aggregate={}, aggregateId={}",
                    event.getAggregate(), event.getAggregateId());
            } catch (Exception e) {
                log.error("Failed to publish outbox event: aggregate={}, aggregateId={}, error={}",
                    event.getAggregate(), event.getAggregateId(), e.getMessage());
            }
        }
    }
}
