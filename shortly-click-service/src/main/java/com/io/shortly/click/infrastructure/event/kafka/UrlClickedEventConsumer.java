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

@Slf4j
@Component
@RequiredArgsConstructor
public class UrlClickedEventConsumer {

    private static final int MAX_INDIVIDUAL_RETRIES = 3;

    private final UrlClickRepository urlClickRepository;
    private final ClickEventDLQPublisher dlqPublisher;

    @Transactional
    @KafkaListener(
            topics = KafkaTopics.URL_CLICKED,
            groupId = KafkaTopics.CLICK_SERVICE_GROUP,
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeUrlClickedBatch(List<UrlClickedEvent> events) {
        if (events.isEmpty()) {
            return;
        }

        try {
            log.debug("[Batch] Processing {} click events", events.size());

            List<UrlClick> urlClicks = events.stream()
                    .map(event -> UrlClick.create(
                            event.getShortCode(),
                            event.getOriginalUrl()
                    ))
                    .toList();

            urlClickRepository.saveAll(urlClicks);

            log.info("[Batch] Successfully processed {} click events", events.size());

        } catch (Exception e) {
            processBatchIndividually(events);
        }
    }

    /**
     * Bulk Insert 실패 시 개별 처리
     */
    private void processBatchIndividually(List<UrlClickedEvent> events) {
        int successCount = 0;
        int failCount = 0;

        for (UrlClickedEvent event : events) {
            boolean success = processWithRetry(event);

            if (success) {
                successCount++;
            } else {
                failCount++;
            }
        }

        log.info("[Batch] Individual processing completed: success={}, failed={}",
                successCount, failCount);
    }

    /**
     * 개별 이벤트 재시도 처리
     *
     * @param event 처리할 이벤트
     * @return 성공 여부
     */
    private boolean processWithRetry(UrlClickedEvent event) {
        Exception exception = null;

        for (int attempt = 1; attempt <= MAX_INDIVIDUAL_RETRIES; attempt++) {
            try {
                UrlClick urlClick = UrlClick.create(
                        event.getShortCode(),
                        event.getOriginalUrl()
                );

                urlClickRepository.save(urlClick);
                return true;

            } catch (Exception e) {
                exception = e;
                if (attempt < MAX_INDIVIDUAL_RETRIES) {
                    // 지수 백오프: 100ms → 200ms → 400ms
                    sleep(100 * (long) Math.pow(2, attempt - 1));
                }
            }
        }

        // 최종 실패: DLQ로 전송
        log.error("[DLQ] Event failed after {} retries, sending to DLQ: shortCode={}",
                MAX_INDIVIDUAL_RETRIES, event.getShortCode());

        dlqPublisher.publishToDLQ(event, exception, MAX_INDIVIDUAL_RETRIES);

        return false;
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[Retry] Sleep interrupted", e);
        }
    }
}
