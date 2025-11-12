package com.io.shortly.redirect.infrastructure.event.kafka;

import com.io.shortly.redirect.domain.RedirectEventPublisher;
import com.io.shortly.shared.event.UrlClickedEvent;
import com.io.shortly.shared.kafka.KafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedirectEventPublisherKafkaImpl implements RedirectEventPublisher {

    private final KafkaTemplate<String, UrlClickedEvent> kafkaTemplate;

    @Override
    public void publishUrlClicked(UrlClickedEvent event) {
        kafkaTemplate.send(KafkaTopics.URL_CLICKED, event.getShortCode(), event)
            .exceptionally(ex -> {
                log.error("[Event] 클릭 이벤트 발행 실패: shortCode={}", event.getShortCode(), ex);
                return null;
            });
    }
}
