package com.io.shortly.url.infrastructure.scheduler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.io.shortly.shared.event.UrlCreatedEvent;
import com.io.shortly.shared.kafka.KafkaTopics;
import com.io.shortly.url.infrastructure.persistence.jpa.outbox.OutboxJpaEntity;
import com.io.shortly.url.infrastructure.persistence.jpa.outbox.OutboxJpaRepository;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
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
    private static final int KAFKA_SEND_TIMEOUT_SECONDS = 5;

    private final OutboxJpaRepository outboxJpaRepository;
    private final KafkaTemplate<String, UrlCreatedEvent> kafkaTemplate;
    private final ObjectMapper objectMapper;

    // Virtual Thread
    // 사용 이유: I/O 작업(Kafka 전송)에 최적화, 플랫폼 스레드보다 경량
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    @Transactional
    @Scheduled(fixedDelay = ONE_SECOND)
    public void relayPendingEvents() {
        List<OutboxJpaEntity> events = outboxJpaRepository.findPendingEvents(
            PageRequest.of(0, BATCH_SIZE)
        );

        if (events.isEmpty()) {
            return;
        }

        List<CompletableFuture<OutboxJpaEntity>> futures = events.stream()
            // Virtual Thread로 병렬 처리
            // 이유: 100개의 이벤트를 동시에 처리 (I/O 대기 시 스레드 차단 없음)
            .map(event -> CompletableFuture.supplyAsync(() -> {
                try {
                    // 이벤트 생성
                    UrlCreatedEvent urlCreatedEvent = objectMapper.readValue(
                        event.getPayload(),
                        UrlCreatedEvent.class
                    );

                    // 이벤트 발행(동기)
                    // 동기 처리 이유: 이벤트 유실 방지 & 실패 이벤트 처리
                    kafkaTemplate.send(
                        KafkaTopics.URL_CREATED,
                        event.getAggregateId(),
                        urlCreatedEvent
                    ).get(KAFKA_SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);

                    return event;
                } catch (Exception e) {
                    log.error("[Outbox] Kafka 발행 실패: aggregate={}, aggregateId={}, error={}",
                        event.getAggregate(), event.getAggregateId(), e.getMessage());
                    return null;
                }
            }, executor))
            .toList();

        // 100개의 이벤트 발행이 완료될 때까지 최대 5초 대기
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .get(KAFKA_SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("[Outbox] 일부 이벤트 발행 실패 또는 타임아웃: {}", e.getMessage());
        }

        // 성공한 이벤트만 수집
        List<OutboxJpaEntity> successEvents = futures.stream()
            .map(CompletableFuture::join)
            .filter(Objects::nonNull)
            .toList();

        // 상태 업데이트
        successEvents.forEach(OutboxJpaEntity::markAsPublished);
        outboxJpaRepository.saveAll(successEvents);

        log.info("[Outbox] 배치 발행 완료: 총 {}건, 성공 {}건",
            events.size(), successEvents.size());
    }
}
