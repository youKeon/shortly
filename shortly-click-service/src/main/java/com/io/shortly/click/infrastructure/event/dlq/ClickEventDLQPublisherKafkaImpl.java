package com.io.shortly.click.infrastructure.event.dlq;

import com.io.shortly.click.domain.ClickEventDLQPublisher;
import com.io.shortly.shared.event.UrlClickedEvent;
import com.io.shortly.shared.kafka.KafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ClickEventDLQPublisherKafkaImpl implements ClickEventDLQPublisher {

    private final KafkaTemplate<String, ClickEventDLQRecord> dlqKafkaTemplate;

    @Override
    public void publishToDLQ(UrlClickedEvent event, Exception exception, int retryCount) {
        try {
            ClickEventDLQRecord dlqRecord = ClickEventDLQRecord.of(
                    event,
                    exception,
                    retryCount,
                    KafkaTopics.URL_CLICKED,
                    KafkaTopics.CLICK_SERVICE_GROUP
            );

            dlqKafkaTemplate.send(
                    KafkaTopics.URL_CLICKED_DLQ,
                    event.getShortCode(),
                    dlqRecord
            ).whenComplete((result, ex) -> {
                if (ex == null) {
                    log.warn("[DLQ] Event sent to DLQ: shortCode={}, errorType={}, retryCount={}",
                            event.getShortCode(),
                            exception.getClass().getSimpleName(),
                            retryCount);
                } else {
                    log.error("[DLQ] Failed to send event to DLQ: shortCode={}, originalError={}, dlqError={}",
                            event.getShortCode(),
                            exception.getMessage(),
                            ex.getMessage());
                }
            });

        } catch (Exception e) {
            log.error("[DLQ] Critical error while publishing to DLQ: shortCode={}, error={}",
                    event.getShortCode(), e.getMessage(), e);
        }
    }
}
