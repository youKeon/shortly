package com.io.shortly.test.unit.kafka.mock;

import com.io.shortly.shared.event.UrlClickedEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Kafka Producer의 테스트용 Fake 구현체
 * 재시도 로직과 DLQ 전송을 시뮬레이션
 */
public class FakeKafkaProducer {

    private final Random random = new Random();
    private double failureRate = 0.0; // 실패율 (0.0 ~ 1.0)
    private int maxRetries = 3;
    private boolean enableBackoffTracking = false;

    private final AtomicInteger totalSentCount = new AtomicInteger(0);
    private final AtomicInteger retryCount = new AtomicInteger(0);
    private final List<UrlClickedEvent> dlqEvents = new ArrayList<>();
    private final List<Long> backoffIntervals = new ArrayList<>();

    /**
     * 이벤트 전송 (재시도 로직 포함)
     * @return 성공 여부
     */
    public boolean send(UrlClickedEvent event) {
        int attempts = 0;
        long lastAttemptTime = System.currentTimeMillis();

        while (attempts <= maxRetries) {
            if (attempts > 0) {
                retryCount.incrementAndGet();

                // Exponential backoff 시뮬레이션
                long backoff = calculateBackoff(attempts);
                if (enableBackoffTracking) {
                    backoffIntervals.add(backoff);
                }

                try {
                    Thread.sleep(backoff);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }

            // 전송 시도
            if (random.nextDouble() >= failureRate) {
                totalSentCount.incrementAndGet();
                return true; // 성공
            }

            attempts++;
        }

        // 최종 실패 → DLQ 전송
        sendToDLQ(event);
        return false;
    }

    private long calculateBackoff(int attempt) {
        // Exponential backoff: 100ms * 2^(attempt-1)
        return 100L * (1L << (attempt - 1));
    }

    private synchronized void sendToDLQ(UrlClickedEvent event) {
        dlqEvents.add(event);
    }

    // Getters and Setters
    public void setFailureRate(double failureRate) {
        if (failureRate < 0.0 || failureRate > 1.0) {
            throw new IllegalArgumentException("Failure rate must be between 0.0 and 1.0");
        }
        this.failureRate = failureRate;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public void setEnableBackoffTracking(boolean enable) {
        this.enableBackoffTracking = enable;
    }

    public int getTotalSentCount() {
        return totalSentCount.get();
    }

    public int getRetryCount() {
        return retryCount.get();
    }

    public int getDlqCount() {
        return dlqEvents.size();
    }

    public synchronized List<UrlClickedEvent> getDlqEvents() {
        return new ArrayList<>(dlqEvents);
    }

    public synchronized List<Long> getBackoffIntervals() {
        return new ArrayList<>(backoffIntervals);
    }

    public void reset() {
        totalSentCount.set(0);
        retryCount.set(0);
        dlqEvents.clear();
        backoffIntervals.clear();
    }
}
