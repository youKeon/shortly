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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
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
                    log.error(
                            "[DLQ] 메시지 처리 실패 - Topic: {}, Partition: {}, Offset: {}, Key: {}, Error: {}",
                            consumerRecord.topic(),
                            consumerRecord.partition(),
                            consumerRecord.offset(),
                            consumerRecord.key(),
                            exception.getMessage());

                    return new TopicPartition(
                            TopicType.URL_CLICKED_DLQ.getTopicName(),
                            consumerRecord.partition());
                });

        // 지수 백오프
        ExponentialBackOff backOff = new ExponentialBackOff(
                100L, // 첫 재시도 대기 시간 (100ms)
                2.0 // 2배씩 증가
        );
        backOff.setMaxInterval(5000L); // 최대 대기 시간 (5초)
        backOff.setMaxElapsedTime(30000L); // 총 재시도 시간 (30초)

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff);

        // 복구 불가능한 예외는 바로 DLQ 전송
        errorHandler.addNotRetryableExceptions(
                DataIntegrityViolationException.class,
                DuplicateKeyException.class
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
