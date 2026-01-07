package com.io.shortly.click.infrastructure.event.kafka;

import com.io.shortly.click.domain.UrlClick;
import com.io.shortly.click.domain.UrlClickRepository;
import com.io.shortly.shared.event.TopicType;
import com.io.shortly.shared.event.UrlClickedEvent;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class UrlClickedEventConsumer {

    private final UrlClickRepository urlClickRepository;
    private final KafkaExceptionClassifier exceptionClassifier;
    private final MeterRegistry meterRegistry;

    @Transactional
    @KafkaListener(
        topics = "#{T(com.io.shortly.shared.event.TopicType).URL_CLICKED.getTopicName()}",
        groupId = TopicType.ConsumerGroups.CLICK_SERVICE,
        containerFactory = "kafkaListenerContainerFactory",
        batch = "true"
    )
    public void consumeUrlClicked(List<UrlClickedEvent> events) {
        if (events.isEmpty()) {
            return;
        }

        log.debug("[Kafka Consumer] 배치 처리 시작 - size={}", events.size());

        int successCount = 0;
        int duplicateCount = 0;
        int transientErrorCount = 0;
        int permanentErrorCount = 0;

        for (UrlClickedEvent event : events) {
            try {
                UrlClick click = UrlClick.create(
                        event.getEventId(),
                        event.getShortCode(),
                        event.getOriginalUrl());
                urlClickRepository.save(click);
                successCount++;

            } catch (Exception e) {
                // 예외 분류
                FailureType failureType = exceptionClassifier.classify(e);

                switch (failureType) {
                    case DUPLICATE -> {
                        // 중복 이벤트 = 이미 DB에 저장되어 있음 = 처리 완료 = 정상
                        // Kafka At-Least-Once 전송 보장으로 인한 정상적인 중복
                        // 예외를 던지지 않음 → DLQ 전송 안 함
                        duplicateCount++;
                        log.debug("[Kafka Consumer] 중복 이벤트 (이미 처리됨) - eventId={}", event.getEventId());
                    }
                    case PERMANENT -> {
                        // 영구적 실패: 즉시 DLQ 전송 (재시도 불필요)
                        permanentErrorCount++;
                        log.error("[Kafka Consumer] 영구적 실패 감지 - eventId={}, shortCode={}, error={}",
                                event.getEventId(), event.getShortCode(), e.getMessage());
                        // 예외를 던짐 → ErrorHandler가 재시도 없이 즉시 DLQ 전송
                        throw new PermanentFailureException("영구적 실패: " + e.getMessage(), e);
                    }
                    case TRANSIENT -> {
                        // 일시적 실패: 재시도 필요
                        transientErrorCount++;
                        log.warn("[Kafka Consumer] 일시적 실패 감지 - eventId={}, shortCode={}, error={}",
                                event.getEventId(), event.getShortCode(), e.getMessage());
                        // 예외를 던짐 → ErrorHandler가 재시도 후 DLQ 전송
                        throw new TransientFailureException("일시적 실패: " + e.getMessage(), e);
                    }
                }
            }
        }

        // 메트릭 기록
        recordMetrics(successCount, duplicateCount, transientErrorCount, permanentErrorCount);

        // 중복/오류 비율 모니터링
        monitorErrorRates(events.size(), duplicateCount, transientErrorCount, permanentErrorCount);

        log.debug("[Kafka Consumer] 배치 처리 완료 - total={}, success={}, duplicate={}, transient={}, permanent={}",
                events.size(), successCount, duplicateCount, transientErrorCount, permanentErrorCount);
    }

    private void recordMetrics(int success, int duplicate, int transientErrors, int permanentErrors) {
        meterRegistry.counter("click.consumer.success").increment(success);
        meterRegistry.counter("click.consumer.duplicate").increment(duplicate);
        meterRegistry.counter("click.consumer.error.transient").increment(transientErrors);
        meterRegistry.counter("click.consumer.error.permanent").increment(permanentErrors);
    }

    private void monitorErrorRates(int total, int duplicate, int transientErrors, int permanentErrors) {
        if (total == 0) {
            return;
        }

        double duplicateRate = (double) duplicate / total * 100;
        double transientRate = (double) transientErrors / total * 100;
        double permanentRate = (double) permanentErrors / total * 100;

        // 중복 비율 높음 경고
        if (duplicateRate > 10.0) {
            log.warn("[Kafka Consumer] 중복 비율 높음 - total={}, duplicates={}, rate={:.2f}%",
                    total, duplicate, duplicateRate);
        }

        // 일시적 오류 비율 높음 경고
        if (transientRate > 5.0) {
            log.warn("[Kafka Consumer] 일시적 오류 비율 높음 - total={}, transient={}, rate={:.2f}%",
                    total, transientErrors, transientRate);
        }

        // 영구적 오류 발생 시 즉시 알림
        if (permanentErrors > 0) {
            log.error("[Kafka Consumer] 영구적 오류 발생 - total={}, permanent={}, rate={:.2f}%",
                    total, permanentErrors, permanentRate);
            // TODO: Slack, Email 알림 전송
        }
    }
}
