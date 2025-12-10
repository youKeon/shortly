package com.io.shortly.redirect.infrastructure.event.kafka;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.backoff.ExponentialBackOff;

@Slf4j
@Configuration
public class KafkaProducerConfig {

    @Bean
    public ExponentialBackOff producerBackOff(
            @Value("${shortly.kafka.producer.initial-interval:100}") long initialInterval,
            @Value("${shortly.kafka.producer.multiplier:2.0}") double multiplier,
            @Value("${shortly.kafka.producer.max-interval:5000}") long maxInterval,
            @Value("${shortly.kafka.producer.max-elapsed-time:30000}") long maxElapsedTime
    ) {
        ExponentialBackOff backOff = new ExponentialBackOff(initialInterval, multiplier);
        backOff.setMaxInterval(maxInterval);
        backOff.setMaxElapsedTime(maxElapsedTime);

        return backOff;
    }
}

