package com.io.shortly.click.infrastructure.event.kafka;

import com.io.shortly.shared.event.UrlClickedEvent;
import com.io.shortly.shared.event.TopicType;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.TopicPartition;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.util.backoff.ExponentialBackOff;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class KafkaConsumerConfig {

    private final KafkaProperties kafkaProperties;
    private final KafkaTemplate<String, UrlClickedEvent> dlqKafkaTemplate;

    @Bean
    public ConsumerFactory<String, UrlClickedEvent> consumerFactory() {
        Map<String, Object> config = kafkaProperties.buildConsumerProperties(null);
        return new DefaultKafkaConsumerFactory<>(config);
    }

    @Bean
    public CommonErrorHandler errorHandler() {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                dlqKafkaTemplate,
                (consumerRecord, exception) -> {

                    String dlqTopic;

                    // 예외 타입에 따라 DLQ 분리
                    if (exception instanceof PermanentFailureException) {
                        // 영구 실패
                        dlqTopic = TopicType.URL_CLICKED_DLQ_PERMANENT.getTopicName();
                        log.error(
                                "[DLQ-Permanent] 영구적 실패 - Topic: {}, Partition: {}, Offset: {}, Error: {}",
                                consumerRecord.topic(),
                                consumerRecord.partition(),
                                consumerRecord.offset(),
                                exception.getMessage());
                    } else {
                        // 일시 실패
                        dlqTopic = TopicType.URL_CLICKED_DLQ_TRANSIENT.getTopicName();
                        log.warn(
                                "[DLQ-Transient] 일시적 실패 (5분 재시도 실패) - Topic: {}, Partition: {}, Offset: {}, Error: {}",
                                consumerRecord.topic(),
                                consumerRecord.partition(),
                                consumerRecord.offset(),
                                exception.getMessage());
                    }

                    return new TopicPartition(dlqTopic, consumerRecord.partition());
                });

        // 지수 백오프 설정
        ExponentialBackOff backOff = new ExponentialBackOff(
                1000L,  // 첫 재시도: 1초
                2.0     // 2배씩 증가
        );
        backOff.setMaxInterval(30000L);     // 최대 대기: 30초
        backOff.setMaxElapsedTime(300000L); // 총 재시도: 5분

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff);

        // 영구적 실패는 재시도 없이 DLQ 전송
        errorHandler.addNotRetryableExceptions(
                PermanentFailureException.class,
                DeserializationException.class,      // JSON 파싱 실패
                IllegalArgumentException.class,      // 비즈니스 예외
                NullPointerException.class          // 필수 필드 누락
        );

        return errorHandler;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, UrlClickedEvent> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, UrlClickedEvent> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        factory.setCommonErrorHandler(errorHandler());
        return factory;
    }
}
