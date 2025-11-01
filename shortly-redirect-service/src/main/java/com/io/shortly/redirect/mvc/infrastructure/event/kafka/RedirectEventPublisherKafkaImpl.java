package com.io.shortly.redirect.mvc.infrastructure.event.kafka;

import com.io.shortly.redirect.mvc.domain.RedirectEventPublisher;
import com.io.shortly.shared.event.UrlClickedEvent;
import com.io.shortly.shared.kafka.KafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Component("mvcRedirectEventPublisher")
@RequiredArgsConstructor
public class RedirectEventPublisherKafkaImpl implements RedirectEventPublisher {

    private final KafkaTemplate<String, UrlClickedEvent> kafkaTemplate;

    @Override
    public void publishUrlClicked(UrlClickedEvent event) {
        String key = event.getShortCode();

        CompletableFuture<SendResult<String, UrlClickedEvent>> future =
            kafkaTemplate.send(KafkaTopics.URL_CLICKED, key, event);

        future.whenComplete((result, ex) -> {
            if (ex == null) {
                log.debug("[Event-MVC] Published: shortCode={}, topic={}, partition={}, offset={}",
                    event.getShortCode(),
                    KafkaTopics.URL_CLICKED,
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset());
            } else {
                log.error("[Event-MVC] Publish failed: shortCode={}, topic={}",
                    event.getShortCode(), KafkaTopics.URL_CLICKED, ex);
            }
        });
    }
}
