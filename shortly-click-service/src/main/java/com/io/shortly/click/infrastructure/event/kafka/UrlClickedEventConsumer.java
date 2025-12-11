package com.io.shortly.click.infrastructure.event.kafka;

import com.io.shortly.click.domain.UrlClick;
import com.io.shortly.click.domain.UrlClickRepository;
import com.io.shortly.shared.event.TopicType;
import com.io.shortly.shared.event.UrlClickedEvent;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class UrlClickedEventConsumer {

    private final UrlClickRepository urlClickRepository;

    @Transactional
    @KafkaListener(topics = "#{T(com.io.shortly.shared.event.TopicType).URL_CLICKED.getTopicName()}", groupId = TopicType.ConsumerGroups.CLICK_SERVICE, containerFactory = "kafkaListenerContainerFactory", batch = "true")
    public void consumeUrlClicked(List<UrlClickedEvent> events) {
        if (events.isEmpty()) {
            return;
        }

        log.debug("[Kafka Consumer] 배치 처리 시작 - size={}", events.size());

        int successCount = 0;
        int duplicateCount = 0;

        for (UrlClickedEvent event : events) {
            try {
                UrlClick click = UrlClick.create(
                        event.getEventId(),
                        event.getShortCode(),
                        event.getOriginalUrl());
                urlClickRepository.save(click);
                successCount++;
            } catch (DataIntegrityViolationException e) {
                duplicateCount++;
                log.trace("[Kafka Retry] 중복 이벤트 스킵 - eventId={}", event.getEventId());
            }
        }

        log.debug("[Kafka Consumer] 배치 처리 완료 - total={}, saved={}, duplicates={}",
                events.size(), successCount, duplicateCount);
    }
}
