package com.io.shortly.click.infrastructure.event.kafka;

import com.io.shortly.click.domain.UrlClick;
import com.io.shortly.click.domain.UrlClickRepository;
import com.io.shortly.shared.event.UrlClickedEvent;
import com.io.shortly.shared.kafka.KafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class UrlClickedEventConsumer {

    private final UrlClickRepository urlClickRepository;

    @KafkaListener(
            topics = KafkaTopics.URL_CLICKED,
            groupId = KafkaTopics.CLICK_SERVICE_GROUP,
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeUrlClicked(UrlClickedEvent event) {
        try {
            UrlClick urlClick = UrlClick.create(
                    event.getShortCode(),
                    event.getOriginalUrl()
            );
            urlClickRepository.save(urlClick);

        } catch (Exception e) {
            log.error("Failed to process click event: {}",  event.getShortCode(), e);
            // TODO: Dead Letter Queue
        }
    }
}
