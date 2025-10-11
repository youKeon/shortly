package com.io.bitly.application.kafka;

import com.io.bitly.application.event.UrlClickedEvent;
import com.io.bitly.domain.click.UrlClick;
import com.io.bitly.domain.click.UrlClickRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Click Event Consumer
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UrlClickEventConsumer {

    private final UrlClickRepository urlClickRepository;

    /**
     * @param events 클릭 이벤트 리스트 (최대 5,000건)
     */
    @KafkaListener(
        topics = "url-clicks",
        groupId = "url-click-consumer-group",
        concurrency = "10",  // 10개 스레드 병렬 처리
        containerFactory = "batchKafkaListenerContainerFactory"
    )
    @Transactional
    public void consumeClickEvents(List<UrlClickedEvent> events, Acknowledgment acknowledgment) {
        if (events.isEmpty()) {
            acknowledgment.acknowledge();
            return;
        }

        List<UrlClick> urlClicks = events.stream()
            .map(event -> UrlClick.of(event.urlId()))
            .toList();

        urlClickRepository.saveAll(urlClicks);

        log.info("[KAFKA] Batch processed: {} clicks saved", events.size());

        // Kafka 오프셋 수동 커밋
        acknowledgment.acknowledge();
    }
}
