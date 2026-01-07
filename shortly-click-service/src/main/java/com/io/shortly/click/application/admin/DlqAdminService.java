package com.io.shortly.click.application.admin;

import com.io.shortly.click.api.admin.dto.DlqReprocessResponse;
import com.io.shortly.click.domain.UrlClick;
import com.io.shortly.click.domain.UrlClickRepository;
import com.io.shortly.click.infrastructure.event.kafka.KafkaExceptionClassifier;
import com.io.shortly.shared.event.TopicType;
import com.io.shortly.shared.event.UrlClickedEvent;
import java.time.Duration;
import java.util.Collections;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DlqAdminService {

    private final ConsumerFactory<String, UrlClickedEvent> consumerFactory;
    private final UrlClickRepository urlClickRepository;
    private final KafkaExceptionClassifier exceptionClassifier;

    /**
     * DLQ 수동 재처리
     */
    @Transactional
    public DlqReprocessResponse reprocessPermanentMessage(long eventId) {
        String dlqTopic = TopicType.URL_CLICKED_DLQ_PERMANENT.getTopicName();

        log.info("[DLQ Admin] Permanent DLQ 수동 재처리 시작 - eventId={}", eventId);

        try (Consumer<String, UrlClickedEvent> consumer = consumerFactory.createConsumer(
            "permanent-dlq-manual-group", null)
        ) {
            consumer.subscribe(Collections.singletonList(dlqTopic));

            ConsumerRecords<String, UrlClickedEvent> records =
                consumer.poll(Duration.ofSeconds(5));

            for (ConsumerRecord<String, UrlClickedEvent> record : records) {
                UrlClickedEvent event = record.value();

                if (event.getEventId() == eventId) {
                    try {
                        UrlClick click = UrlClick.create(
                            event.getEventId(),
                            event.getShortCode(),
                            event.getOriginalUrl());
                        urlClickRepository.save(click);

                        consumer.commitSync();
                        log.info("[DLQ Admin] Permanent DLQ 재처리 성공 - eventId={}", eventId);

                        return DlqReprocessResponse.of(1, 1, 0, "SUCCESS");

                    } catch (Exception e) {
                        log.error("[DLQ Admin] Permanent DLQ 재처리 실패 - eventId={}, error={}",
                            eventId, e.getMessage());
                        return DlqReprocessResponse.of(1, 0, 1, "FAILED");
                    }
                }
            }

            log.warn("[DLQ Admin] Permanent DLQ에서 메시지를 찾을 수 없음 - eventId={}", eventId);
            return DlqReprocessResponse.of(0, 0, 0, "NOT_FOUND");

        } catch (Exception e) {
            log.error("[DLQ Admin] Permanent DLQ 재처리 중 오류", e);
            return DlqReprocessResponse.of(0, 0, 0, "ERROR");
        }
    }
}
