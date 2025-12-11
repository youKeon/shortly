package com.io.shortly.redirect.infrastructure.event.kafka;

import static com.io.shortly.shared.event.TopicType.URL_CLICKED;

import com.io.shortly.redirect.domain.RedirectEventPublisher;
import com.io.shortly.shared.event.UrlClickedEvent;
import com.io.shortly.shared.event.TopicType;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.backoff.BackOffExecution;
import org.springframework.util.backoff.ExponentialBackOff;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedirectEventPublisherKafkaImpl implements RedirectEventPublisher {

    private static final String MAIN_TOPIC = URL_CLICKED.getTopicName();
    private static final String DLQ_TOPIC = TopicType.URL_CLICKED_DLQ.getTopicName();
    private static final Executor ASYNC_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    private final KafkaTemplate<String, UrlClickedEvent> kafkaTemplate;
    private final ExponentialBackOff producerBackOff;

    @Override
    public void publishUrlClicked(UrlClickedEvent event) {
        CompletableFuture.runAsync(() -> publish(event, producerBackOff.start()), ASYNC_EXECUTOR);
    }

    private void publish(UrlClickedEvent event, BackOffExecution backOff) {
        kafkaTemplate.send(MAIN_TOPIC, event.getShortCode(), event)
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        log.debug("[Event] 클릭 이벤트 발행 성공: eventId={}", event.getEventId());
                        return;
                    }

                    long nextBackOff = backOff.nextBackOff();
                    if (nextBackOff != BackOffExecution.STOP) {
                        log.warn("[Event] 발행 실패, {}ms 후 재시도 - eventId={}, error={}",
                                nextBackOff, event.getEventId(), ex.getMessage());
                        CompletableFuture.delayedExecutor(nextBackOff, TimeUnit.MILLISECONDS)
                                .execute(() -> publish(event, backOff));
                    } else {
                        log.error("[Event] 발행 최종 실패, DLQ 전송 - eventId={}", event.getEventId());
                        kafkaTemplate.send(DLQ_TOPIC, event.getShortCode(), event);
                    }
                });
    }
}
