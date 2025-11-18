package com.io.shortly.redirect.infrastructure.kafka;

import com.io.shortly.redirect.domain.RedirectEventPublisher;
import com.io.shortly.shared.event.UrlClickedEvent;
import com.io.shortly.shared.kafka.KafkaTopics;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedirectEventPublisherKafkaImpl implements RedirectEventPublisher {

    private final KafkaTemplate<String, UrlClickedEvent> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    @Override
    @CircuitBreaker(name = "kafkaClickEvent", fallbackMethod = "publishFallback")
    public void publishUrlClicked(UrlClickedEvent event) {
        kafkaTemplate.send(KafkaTopics.URL_CLICKED, event.getShortCode(), event)
            .thenAccept(result -> {
                meterRegistry.counter("click_event.published.success").increment();
                log.debug("[Event] 클릭 이벤트 발행 성공: shortCode={}, offset={}",
                    event.getShortCode(),
                    result.getRecordMetadata().offset());
            })
            .exceptionally(ex -> {
                meterRegistry.counter("click_event.published.failure").increment();
                log.error("[Event] 클릭 이벤트 발행 실패: shortCode={}", event.getShortCode(), ex);
                return null;
            });
    }

    /**
     * Kafka 장애로 Circuit이 Open되면 이벤트 발행을 생략하고 즉시 반환
     */
    private void publishFallback(UrlClickedEvent event, Exception e) {
        meterRegistry.counter("click_event.published.circuit_open").increment();
        log.warn("[Event] Circuit Open - 클릭 이벤트 유실 (Kafka 장애): shortCode={}, reason={}",
            event.getShortCode(), e.getMessage());
    }

}
