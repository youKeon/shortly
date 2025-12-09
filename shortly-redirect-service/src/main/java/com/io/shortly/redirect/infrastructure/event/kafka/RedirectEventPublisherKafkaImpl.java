package com.io.shortly.redirect.infrastructure.event.kafka;

import com.io.shortly.redirect.domain.RedirectEventPublisher;
import com.io.shortly.shared.event.UrlClickedEvent;
import com.io.shortly.shared.kafka.TopicType;
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
        kafkaTemplate.send(TopicType.URL_CLICKED.getTopicName(), event.getShortCode(), event)
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        log.debug("[Event] 클릭 이벤트 발행 성공: eventId={}, shortCode={}",
                                event.getEventId(), event.getShortCode());
                    } else {
                        log.error("[Event] 클릭 이벤트 발행 실패 - eventId={}, shortCode={}, error={}",
                                event.getEventId(), event.getShortCode(), ex.getMessage());
                    }
                });
    }

}
