package com.io.bitly.application.kafka;

import com.io.bitly.application.event.UrlClickedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Click Event Producer
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UrlClickEventProducer {

    private static final String TOPIC = "url-clicks";

    private final KafkaTemplate<String, UrlClickedEvent> kafkaTemplate;

    public void sendClickEvent(UrlClickedEvent event) {
        try {
            kafkaTemplate.send(TOPIC, event.shortCode(), event);
            log.debug("[KAFKA] Click event sent: urlId={}, shortCode={}", event.urlId(), event.shortCode());
        } catch (Exception e) {
            log.error("[KAFKA] Failed to send click event: urlId={}, shortCode={}",
                event.urlId(), event.shortCode(), e);
        }
    }
}
