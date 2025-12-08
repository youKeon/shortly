package com.io.shortly.click.infrastructure.event.kafka;

import com.io.shortly.shared.event.UrlClickedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.Map;

@Configuration
@RequiredArgsConstructor
public class KafkaProducerConfig {

    private final KafkaProperties kafkaProperties;

    @Bean
    public ProducerFactory<String, UrlClickedEvent> dlqProducerFactory() {
        Map<String, Object> config = kafkaProperties.buildProducerProperties(null);
        config.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);
        return new DefaultKafkaProducerFactory<>(config);
    }

    @Bean
    public KafkaTemplate<String, UrlClickedEvent> dlqKafkaTemplate() {
        return new KafkaTemplate<>(dlqProducerFactory());
    }
}
