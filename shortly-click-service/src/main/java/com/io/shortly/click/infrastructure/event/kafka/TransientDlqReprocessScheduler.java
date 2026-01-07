package com.io.shortly.click.infrastructure.event.kafka;

import com.io.shortly.click.domain.UrlClick;
import com.io.shortly.click.domain.UrlClickRepository;
import com.io.shortly.shared.event.TopicType;
import com.io.shortly.shared.event.UrlClickedEvent;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.Collections;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TransientDlqReprocessScheduler {

    private final ConsumerFactory<String, UrlClickedEvent> consumerFactory;
    private final KafkaTemplate<String, UrlClickedEvent> dlqKafkaTemplate;
    private final UrlClickRepository urlClickRepository;
    private final KafkaExceptionClassifier exceptionClassifier;
    private final MeterRegistry meterRegistry;

    /**
     * 10분마다 DLQ 재처리
     */
    @Scheduled(fixedDelay = 600000, initialDelay = 60000) // 10분마다, 첫 실행 1분 후
    public void reprocessTransientDlq() {
        String dlqTopic = TopicType.URL_CLICKED_DLQ_TRANSIENT.getTopicName();

        log.info("[DLQ Reprocess] Transient DLQ 재처리 시작 - topic={}", dlqTopic);

        try (Consumer<String, UrlClickedEvent> consumer = consumerFactory.createConsumer(
            "dlq-reprocess-group", null)
        ) {
            consumer.subscribe(Collections.singletonList(dlqTopic));

            ConsumerRecords<String, UrlClickedEvent> records =
                consumer.poll(Duration.ofSeconds(10));

            if (records.isEmpty()) {
                log.debug("[DLQ Reprocess] 재처리할 메시지 없음");
                return;
            }

            int successCount = 0;
            int failedCount = 0;
            int permanentCount = 0;

            for (ConsumerRecord<String, UrlClickedEvent> record : records) {
                UrlClickedEvent event = record.value();

                try {
                    UrlClick click = UrlClick.create(
                            event.getEventId(),
                            event.getShortCode(),
                            event.getOriginalUrl()
                    );
                    urlClickRepository.save(click);

                    successCount++;
                    log.info("[DLQ Reprocess] 재처리 성공 - eventId={}", event.getEventId());

                } catch (Exception e) {
                    FailureType failureType = exceptionClassifier.classify(e);

                    switch (failureType) {
                        case DUPLICATE -> {
                            // 중복은 성공으로 간주하여 DLQ에서 제거
                            successCount++;
                            log.debug("[DLQ Reprocess] 중복 이벤트 (이미 처리됨) - eventId={}", event.getEventId());
                        }
                        case PERMANENT -> {
                            // 영구적 실패는 Permanent DLQ로 이동
                            permanentCount++;
                            sendToPermanentDlq(event, e);
                            log.error("[DLQ Reprocess] 영구적 실패로 판정, Permanent DLQ 이동 - eventId={}",
                                event.getEventId());
                        }
                        case TRANSIENT -> {
                            // 일시적 실패는 다음 스케줄에 재시도 (커밋 안 함)
                            failedCount++;
                            log.warn("[DLQ Reprocess] 재처리 실패 (다음에 재시도) - eventId={}, error={}",
                                event.getEventId(), e.getMessage());
                        }
                    }
                }
            }

            // 성공한 메시지만 커밋 (실패한 메시지는 다음 스케줄에 재시도)
            if (successCount > 0 || permanentCount > 0) {
                consumer.commitSync();
            }

            // 메트릭 기록
            recordMetrics(successCount, failedCount, permanentCount);

            log.info("[DLQ Reprocess] 재처리 완료 - total={}, success={}, failed={}, permanent={}",
                records.count(), successCount, failedCount, permanentCount);

        } catch (Exception e) {
            log.error("[DLQ Reprocess] 재처리 중 오류 발생", e);
        }
    }

    private void sendToPermanentDlq(UrlClickedEvent event, Exception exception) {
        try {
            dlqKafkaTemplate.send(
                TopicType.URL_CLICKED_DLQ_PERMANENT.getTopicName(),
                event
            );
        } catch (Exception e) {
            log.error("[DLQ Reprocess] Permanent DLQ 전송 실패 - eventId={}",
                    event.getEventId(), e);
        }
    }

    private void recordMetrics(int success, int failed, int permanent) {
        meterRegistry.counter("dlq.reprocess.success").increment(success);
        meterRegistry.counter("dlq.reprocess.failed").increment(failed);
        meterRegistry.counter("dlq.reprocess.permanent").increment(permanent);
    }
}
