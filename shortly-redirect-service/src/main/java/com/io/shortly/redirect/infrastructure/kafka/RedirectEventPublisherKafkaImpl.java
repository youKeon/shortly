package com.io.shortly.redirect.infrastructure.kafka;

import com.io.shortly.redirect.domain.RedirectEventPublisher;
import com.io.shortly.shared.event.UrlClickedEvent;
import com.io.shortly.shared.kafka.KafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedirectEventPublisherKafkaImpl implements RedirectEventPublisher {

    private final KafkaTemplate<String, UrlClickedEvent> kafkaTemplate;

    @Async
    @Override
    public void publishUrlClicked(UrlClickedEvent event) {
        try {
            kafkaTemplate.send(KafkaTopics.URL_CLICKED, event.getShortCode(), event);
            log.debug("[Event] 클릭 이벤트 발행 시도: shortCode={}", event.getShortCode());

        } catch (Exception e) {
            log.warn("[Event] 클릭 이벤트 발행 실패 (Kafka 장애): shortCode={}, reason={}",
                    event.getShortCode(), e.getMessage()
            );
        }
    }

}
