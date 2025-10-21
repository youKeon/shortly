package com.io.shortly.url.infrastructure.event.kafka;

import com.io.shortly.shared.event.UrlCreatedEvent;
import com.io.shortly.shared.kafka.KafkaTopics;
import com.io.shortly.url.domain.event.UrlEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class UrlEventPublisherKafkaImpl implements UrlEventPublisher {

    private final KafkaTemplate<String, UrlCreatedEvent> kafkaTemplate;

    @Override
    public void publishUrlCreated(UrlCreatedEvent event) {
        String key = event.getShortCode();

        CompletableFuture<SendResult<String, UrlCreatedEvent>> future =
            kafkaTemplate.send(KafkaTopics.URL_CREATED, key, event);

        future.whenComplete((result, ex) -> {
            if (ex == null) {
                log.info("Published UrlCreatedEvent: shortCode={}, partition={}, offset={}",
                    event.getShortCode(),
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset());
            } else {
                log.error("Failed to publish UrlCreatedEvent: shortCode={}", event.getShortCode(), ex);
            }
        });
    }
}
