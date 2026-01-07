package com.io.shortly.click.infrastructure.event.kafka;

import com.io.shortly.shared.event.UrlClickedEvent;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PermanentDlqMonitor {

    private final MeterRegistry meterRegistry;

    @KafkaListener(
        topics = "#{T(com.io.shortly.shared.event.TopicType).URL_CLICKED_DLQ_PERMANENT.getTopicName()}",
        groupId = "permanent-dlq-monitor-group"
    )
    public void monitorPermanentDlq(UrlClickedEvent event) {
        log.error("[ALERT] Permanent DLQ 메시지 감지 - eventId={}, shortCode={}, originalUrl={}",
                event.getEventId(), event.getShortCode(), event.getOriginalUrl());

        // 메트릭 기록
        meterRegistry.counter("dlq.permanent.received").increment();

        // Slack 알림 전송
        sendAlert(String.format(
                "🚨 Permanent DLQ 메시지 발생\n" +
                "- Event ID: %d\n" +
                "- Short Code: %s\n" +
                "- Original URL: %s\n" +
                "- Action: 수동 확인 필요\n" +
                "- 조치: /api/v1/admin/dlq/permanent/reprocess/%d 로 재처리 가능",
                event.getEventId(),
                event.getShortCode(),
                event.getOriginalUrl(),
                event.getEventId()
        ));
    }

    private void sendAlert(String message) {
        // TODO: 알림 전송
        log.error("[ALERT] {}", message);
    }
}
