package com.io.shortly.click.infrastructure.event.kafka;

import com.io.shortly.click.domain.ClickEventDLQPublisher;
import com.io.shortly.click.domain.UrlClick;
import com.io.shortly.click.domain.UrlClickRepository;
import com.io.shortly.shared.event.UrlClickedEvent;
import com.io.shortly.shared.kafka.KafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
public class UrlClickedEventConsumer {

    private static final int MAX_INDIVIDUAL_RETRIES = 3;

    private final UrlClickRepository urlClickRepository;
    private final ClickEventDLQPublisher dlqPublisher;

    @Transactional
    @KafkaListener(topics = KafkaTopics.URL_CLICKED, groupId = KafkaTopics.CLICK_SERVICE_GROUP, containerFactory = "kafkaListenerContainerFactory")
    public void consumeUrlClickedBatch(List<UrlClickedEvent> events) {
        if (events.isEmpty()) {
            return;
        }

        try {
            List<UrlClick> urlClicks = events.stream()
                    .map(event -> UrlClick.create(
                            event.getShortCode(),
                            event.getOriginalUrl())
                    )
                    .toList();

            urlClickRepository.saveAll(urlClicks);
        } catch (Exception e) {
            processBatchIndividually(events);
        }
    }

    private void processBatchIndividually(List<UrlClickedEvent> events) {
        for (UrlClickedEvent event : events) {
            processWithRetry(event);
        }
    }

    private void processWithRetry(UrlClickedEvent event) {
        Exception exception = null;

        for (int attempt = 1; attempt <= MAX_INDIVIDUAL_RETRIES; attempt++) {
            try {
                UrlClick urlClick = UrlClick.create(
                        event.getShortCode(),
                        event.getOriginalUrl());

                urlClickRepository.save(urlClick);
            } catch (Exception e) {
                exception = e;
                if (attempt < MAX_INDIVIDUAL_RETRIES) {
                    // 지수 백오프: 100ms → 200ms → 400ms
                    sleep(100 * (long) Math.pow(2, attempt - 1));
                }
            }
        }
        dlqPublisher.publishToDLQ(event, exception, MAX_INDIVIDUAL_RETRIES);
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
