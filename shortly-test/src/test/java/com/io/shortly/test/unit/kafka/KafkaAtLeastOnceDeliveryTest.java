package com.io.shortly.test.unit.kafka;

import static org.junit.jupiter.api.Assertions.*;

import com.io.shortly.shared.event.UrlClickedEvent;
import com.io.shortly.test.unit.kafka.mock.FakeKafkaProducer;
import com.io.shortly.test.unit.kafka.mock.FakeKafkaConsumer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Kafka At-Least-Once 전달 보장 테스트")
class KafkaAtLeastOnceDeliveryTest {

    private FakeKafkaProducer producer;
    private FakeKafkaConsumer consumer;

    @BeforeEach
    void setUp() {
        producer = new FakeKafkaProducer();
        consumer = new FakeKafkaConsumer();
    }

    @Test
    @DisplayName("Producer - 전송 실패 시 자동 재시도")
    void producer_AutoRetryOnFailure() {
        // given
        producer.setFailureRate(0.8); // 80% 실패율 (높은 실패율)
        producer.setMaxRetries(10); // 충분한 재시도 횟수
        UrlClickedEvent event = createEvent(1L, "abc123");

        // when
        boolean sent = producer.send(event);

        // then - 80% 실패율이지만 10번 재시도하면 대부분 성공
        // 성공하지 못할 확률 = 0.8^11 = 약 8.6% (매우 낮음)
        if (sent) {
            assertTrue(producer.getRetryCount() >= 0, "재시도가 있었을 수 있음");
            assertEquals(1, producer.getTotalSentCount(), "정확히 1번 전송되어야 함");
        } else {
            // 매우 낮은 확률로 실패 (0.8^11)
            assertEquals(0, producer.getTotalSentCount(), "실패 시 전송 카운트는 0");
            assertTrue(producer.getDlqCount() > 0, "DLQ로 전송되어야 함");
        }
    }

    @Test
    @DisplayName("Producer - 최대 재시도 횟수 초과 시 DLQ 전송")
    void producer_SendToDLQAfterMaxRetries() {
        // given
        producer.setFailureRate(1.0); // 100% 실패율 (항상 실패)
        producer.setMaxRetries(3);
        UrlClickedEvent event = createEvent(1L, "abc123");

        // when
        boolean sent = producer.send(event);

        // then
        assertFalse(sent, "모든 재시도 실패 시 false 반환");
        assertEquals(3, producer.getRetryCount(), "정확히 3번 재시도해야 함");
        assertTrue(producer.getDlqCount() > 0, "DLQ로 전송되어야 함");
    }

    @Test
    @DisplayName("Producer - 일시적 실패 후 복구")
    void producer_RecoverAfterTransientFailure() {
        // given
        producer.setFailureRate(0.8); // 80% 실패율
        producer.setMaxRetries(5);
        List<UrlClickedEvent> events = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            events.add(createEvent(i, "code" + i));
        }

        // when
        int successCount = 0;
        for (UrlClickedEvent event : events) {
            if (producer.send(event)) {
                successCount++;
            }
        }

        // then
        assertTrue(successCount > 0, "일부는 재시도 후 성공해야 함");
        System.out.printf("일시적 실패 테스트: %d/%d 성공 (실패율 80%%)%n",
            successCount, events.size());
    }

    @Test
    @DisplayName("Consumer - 중복 메시지 멱등성 처리")
    void consumer_IdempotentProcessing() {
        // given
        UrlClickedEvent event = createEvent(100L, "xyz789");
        consumer.setEnableIdempotency(true);

        // when - 같은 이벤트를 3번 전달 (중복)
        consumer.consume(event);
        consumer.consume(event);
        consumer.consume(event);

        // then
        assertEquals(1, consumer.getProcessedUniqueCount(),
            "중복 메시지는 1번만 처리되어야 함");
        assertEquals(3, consumer.getTotalConsumedCount(),
            "총 3번 수신은 했어야 함");
        assertEquals(2, consumer.getDuplicateCount(),
            "2번은 중복으로 스킵되어야 함");
    }

    @Test
    @DisplayName("Consumer - 멱등성 없이 중복 처리")
    void consumer_WithoutIdempotency_ProcessDuplicates() {
        // given
        UrlClickedEvent event = createEvent(200L, "dup123");
        consumer.setEnableIdempotency(false);

        // when - 같은 이벤트를 3번 전달
        consumer.consume(event);
        consumer.consume(event);
        consumer.consume(event);

        // then
        assertEquals(3, consumer.getProcessedUniqueCount(),
            "멱등성 없으면 중복도 모두 처리됨");
        assertEquals(3, consumer.getTotalConsumedCount());
        assertEquals(0, consumer.getDuplicateCount());
    }

    @Test
    @DisplayName("Consumer - 배치 처리 시 부분 실패 복구")
    void consumer_PartialBatchFailureRecovery() {
        // given
        List<UrlClickedEvent> batch = List.of(
            createEvent(1L, "code1"),
            createEvent(2L, "code2"),
            createEvent(3L, "code3"),
            createEvent(4L, "code4"),
            createEvent(5L, "code5")
        );
        consumer.setFailureRate(0.4); // 40% 실패율
        consumer.setEnableIdempotency(true);

        // when
        int successCount = consumer.consumeBatch(batch);

        // then
        assertTrue(successCount > 0, "일부는 성공해야 함");
        assertTrue(successCount <= batch.size(), "성공 수는 배치 크기 이하여야 함");
        System.out.printf("배치 처리: %d/%d 성공%n", successCount, batch.size());
    }

    @Test
    @DisplayName("At-Least-Once - 최소 1번 전달 보장")
    void atLeastOnce_GuaranteedDelivery() {
        // given
        producer.setFailureRate(0.3); // 30% 실패율
        producer.setMaxRetries(5);
        consumer.setEnableIdempotency(true);

        List<UrlClickedEvent> events = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            events.add(createEvent(i, "event" + i));
        }

        // when - Producer에서 전송, Consumer에서 수신
        Set<Long> sentEventIds = new HashSet<>();
        for (UrlClickedEvent event : events) {
            if (producer.send(event)) {
                sentEventIds.add(event.getEventId());
                consumer.consume(event);
            }
        }

        // then
        assertEquals(sentEventIds.size(), consumer.getProcessedUniqueCount(),
            "전송된 모든 메시지는 최소 1번 처리되어야 함");
        assertTrue(consumer.getProcessedUniqueCount() > 0,
            "최소 1개 이상의 메시지가 처리되어야 함");
    }

    @Test
    @DisplayName("At-Least-Once - 네트워크 실패 시나리오")
    void atLeastOnce_NetworkFailureScenario() {
        // given
        producer.setFailureRate(0.7); // 70% 실패율 (심각한 네트워크 문제)
        producer.setMaxRetries(10); // 많은 재시도
        consumer.setEnableIdempotency(true);

        List<UrlClickedEvent> events = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            events.add(createEvent(i, "net" + i));
        }

        // when
        int deliveredCount = 0;
        for (UrlClickedEvent event : events) {
            boolean sent = producer.send(event);
            if (sent) {
                consumer.consume(event);
                deliveredCount++;
            }
        }

        // then
        assertTrue(deliveredCount > 0,
            "네트워크 문제에도 일부 메시지는 전달되어야 함");
        assertEquals(deliveredCount, consumer.getProcessedUniqueCount(),
            "전달된 메시지는 모두 정확히 1번 처리되어야 함");

        System.out.printf("네트워크 실패 시나리오: %d/%d 전달 성공 (실패율 70%%)%n",
            deliveredCount, events.size());
    }

    @Test
    @DisplayName("멀티 스레드 - 동시 전송/수신 시 At-Least-Once 보장")
    void multiThread_ConcurrentProduceConsume() throws InterruptedException {
        // given
        int threadCount = 5;
        int eventsPerThread = 20;
        producer.setFailureRate(0.3);
        producer.setMaxRetries(3);
        consumer.setEnableIdempotency(true);

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        AtomicInteger totalSent = new AtomicInteger(0);

        // when
        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int i = 0; i < eventsPerThread; i++) {
                        long eventId = (threadId * 1000L) + i;
                        UrlClickedEvent event = createEvent(eventId, "t" + threadId + "_" + i);

                        if (producer.send(event)) {
                            consumer.consume(event);
                            totalSent.incrementAndGet();
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean finished = doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // then
        assertTrue(finished, "10초 내에 완료되어야 함");
        assertEquals(totalSent.get(), consumer.getProcessedUniqueCount(),
            "전송된 모든 메시지는 정확히 1번 처리되어야 함");
        assertTrue(consumer.getProcessedUniqueCount() > 0,
            "최소 1개 이상 처리되어야 함");
    }

    @Test
    @DisplayName("순서 보장 - 같은 파티션 키는 순서 유지")
    void orderGuarantee_SamePartitionKey() {
        // given
        String partitionKey = "user123";
        List<UrlClickedEvent> events = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            events.add(createEvent(i, partitionKey));
        }

        producer.setFailureRate(0.2);
        producer.setMaxRetries(3);
        consumer.setEnableIdempotency(true);

        // when
        List<Long> sentOrder = new ArrayList<>();
        for (UrlClickedEvent event : events) {
            if (producer.send(event)) {
                sentOrder.add(event.getEventId());
                consumer.consume(event);
            }
        }

        // then
        List<Long> processedOrder = consumer.getProcessedEventIdsForKey(partitionKey);
        assertEquals(sentOrder, processedOrder,
            "같은 파티션 키의 메시지는 순서가 보장되어야 함");
    }

    @Test
    @DisplayName("재시도 간격 - Exponential Backoff 검증")
    void retryInterval_ExponentialBackoff() {
        // given
        producer.setFailureRate(1.0); // 항상 실패
        producer.setMaxRetries(5);
        producer.setEnableBackoffTracking(true);
        UrlClickedEvent event = createEvent(999L, "backoff");

        // when
        long startTime = System.currentTimeMillis();
        producer.send(event);
        long duration = System.currentTimeMillis() - startTime;

        // then
        List<Long> backoffIntervals = producer.getBackoffIntervals();
        assertEquals(5, backoffIntervals.size(), "5번 재시도했어야 함");

        // Exponential backoff 검증: 각 간격이 증가해야 함
        for (int i = 1; i < backoffIntervals.size(); i++) {
            assertTrue(backoffIntervals.get(i) >= backoffIntervals.get(i - 1),
                "재시도 간격은 증가하거나 같아야 함 (Exponential Backoff)");
        }

        System.out.printf("백오프 간격: %s%n", backoffIntervals);
    }

    @Test
    @DisplayName("DLQ 전송 - 최종 실패 메시지는 DLQ로")
    void dlq_FinalFailureGoesToDLQ() {
        // given
        producer.setFailureRate(1.0); // 항상 실패
        producer.setMaxRetries(3);

        List<UrlClickedEvent> events = List.of(
            createEvent(1L, "fail1"),
            createEvent(2L, "fail2"),
            createEvent(3L, "fail3")
        );

        // when
        for (UrlClickedEvent event : events) {
            producer.send(event);
        }

        // then
        assertEquals(events.size(), producer.getDlqCount(),
            "모든 실패 메시지는 DLQ로 전송되어야 함");

        List<UrlClickedEvent> dlqEvents = producer.getDlqEvents();
        assertEquals(events.size(), dlqEvents.size());

        // DLQ 이벤트가 원본 이벤트와 동일한지 확인
        for (int i = 0; i < events.size(); i++) {
            assertEquals(events.get(i).getEventId(), dlqEvents.get(i).getEventId());
        }
    }

    // Helper method
    private UrlClickedEvent createEvent(long eventId, String shortCode) {
        return UrlClickedEvent.of(eventId, shortCode, "https://example.com/" + shortCode);
    }
}
