package com.io.shortly.click.infrastructure.event.kafka;

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
        ).exceptionally(ex -> {
            log.error("[DLQ] DLQ 전송 실패: shortCode={}, originalError={}, dlqError={}",
                    event.getShortCode(),
                    exception.getMessage(),
                    ex.getMessage());
            return null;
        });
    }
}
