package com.io.shortly.test.unit.kafka.mock;

import com.io.shortly.shared.event.UrlClickedEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Kafka Consumer의 테스트용 Fake 구현체
 * 멱등성 처리와 중복 감지 시뮬레이션
 */
public class FakeKafkaConsumer {

    private final Random random = new Random();
    private boolean enableIdempotency = true;
    private double failureRate = 0.0;

    private final Set<Long> processedEventIds = new HashSet<>();
    private final AtomicInteger totalConsumedCount = new AtomicInteger(0);
    private final AtomicInteger processedCount = new AtomicInteger(0);  // 실제 처리 횟수
    private final AtomicInteger duplicateCount = new AtomicInteger(0);
    private final Map<String, List<Long>> partitionKeyOrder = new HashMap<>();

    /**
     * 단일 이벤트 소비
     * @return 처리 성공 여부
     */
    public boolean consume(UrlClickedEvent event) {
        totalConsumedCount.incrementAndGet();

        // 실패 시뮬레이션
        if (random.nextDouble() < failureRate) {
            return false;
        }

        // 멱등성 처리
        synchronized (processedEventIds) {
            if (enableIdempotency) {
                if (processedEventIds.contains(event.getEventId())) {
                    duplicateCount.incrementAndGet();
                    return true; // 중복이지만 성공으로 처리 (이미 처리됨)
                }
            }
            processedEventIds.add(event.getEventId());
        }

        // 실제 처리 카운트 증가 (멱등성 여부와 관계없이)
        processedCount.incrementAndGet();

        // 파티션 키별 순서 추적
        trackPartitionKeyOrder(event);

        return true;
    }

    /**
     * 배치 이벤트 소비
     * @return 성공적으로 처리된 개수
     */
    public int consumeBatch(List<UrlClickedEvent> events) {
        int successCount = 0;
        for (UrlClickedEvent event : events) {
            if (consume(event)) {
                successCount++;
            }
        }
        return successCount;
    }

    private synchronized void trackPartitionKeyOrder(UrlClickedEvent event) {
        String key = event.getShortCode();
        partitionKeyOrder.computeIfAbsent(key, k -> new ArrayList<>())
            .add(event.getEventId());
    }

    // Getters and Setters
    public void setEnableIdempotency(boolean enable) {
        this.enableIdempotency = enable;
    }

    public void setFailureRate(double failureRate) {
        if (failureRate < 0.0 || failureRate > 1.0) {
            throw new IllegalArgumentException("Failure rate must be between 0.0 and 1.0");
        }
        this.failureRate = failureRate;
    }

    public int getTotalConsumedCount() {
        return totalConsumedCount.get();
    }

    public int getProcessedUniqueCount() {
        if (enableIdempotency) {
            synchronized (processedEventIds) {
                return processedEventIds.size();  // 멱등성: 고유 ID 개수
            }
        } else {
            return processedCount.get();  // 비멱등성: 실제 처리 횟수
        }
    }

    public int getDuplicateCount() {
        return duplicateCount.get();
    }

    public synchronized List<Long> getProcessedEventIdsForKey(String partitionKey) {
        return new ArrayList<>(partitionKeyOrder.getOrDefault(partitionKey, List.of()));
    }

    public void reset() {
        synchronized (processedEventIds) {
            processedEventIds.clear();
        }
        totalConsumedCount.set(0);
        processedCount.set(0);
        duplicateCount.set(0);
        synchronized (this) {
            partitionKeyOrder.clear();
        }
    }
}
