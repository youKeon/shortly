package com.io.shortly.click.infrastructure.event.kafka;

import com.io.shortly.click.domain.UrlClick;
import com.io.shortly.click.domain.UrlClickRepository;
import com.io.shortly.shared.event.UrlClickedEvent;
import com.io.shortly.shared.kafka.TopicType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class UrlClickedEventConsumer {

    private final UrlClickRepository urlClickRepository;

    @Transactional
    @KafkaListener(topics = "#{T(com.io.shortly.shared.kafka.TopicType).URL_CLICKED.getTopicName()}", groupId = TopicType.ConsumerGroups.CLICK_SERVICE, containerFactory = "kafkaListenerContainerFactory", batch = "true")
    public void consumeUrlClicked(List<UrlClickedEvent> events) {
        if (events.isEmpty()) {
            return;
        }

        log.debug("[Kafka Batch Consumer] 배치 처리 시작 - size={}", events.size());

        List<UrlClick> urlClicks = events.stream()
                .map(event -> UrlClick.create(
                        event.getEventId(),
                        event.getShortCode(),
                        event.getOriginalUrl()))
                .toList();

        try {
            urlClickRepository.saveAll(urlClicks);
        } catch (DataIntegrityViolationException e) {
            log.warn("[Kafka Batch Consumer] 배치 저장 실패, 개별 처리로 폴백 - size={}", events.size());
            processFallback(events);
        }
    }

    private void processFallback(List<UrlClickedEvent> events) {
        int successCount = 0;
        int duplicateCount = 0;

        for (UrlClickedEvent event : events) {
            try {
                UrlClick urlClick = UrlClick.create(
                        event.getEventId(),
                        event.getShortCode(),
                        event.getOriginalUrl());

                urlClickRepository.save(urlClick);
                successCount++;

            } catch (DataIntegrityViolationException e) {
                duplicateCount++;
            }
        }

        log.debug("[Kafka Batch Consumer] 폴백 처리 완료 - total={}, success={}, duplicate={}",
                events.size(), successCount, duplicateCount);
    }
}
