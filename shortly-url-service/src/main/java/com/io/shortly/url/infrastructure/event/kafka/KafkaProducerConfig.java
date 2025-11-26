package com.io.shortly.url.infrastructure.event.kafka;

import com.io.shortly.shared.event.UrlCreatedEvent;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

@Configuration
@RequiredArgsConstructor
public class KafkaProducerConfig {

    private final KafkaProperties kafkaProperties;

    @Value("${shortly.kafka.topic.url-created.name:url-created}")
    private String topicName;

    @Value("${shortly.kafka.topic.url-created.partitions:1}")
    private int partitions;

    @Value("${shortly.kafka.topic.url-created.replicas:1}")
    private int replicas;

    @Bean
    public ProducerFactory<String, UrlCreatedEvent> producerFactory() {
        Map<String, Object> config = kafkaProperties.buildProducerProperties(null);
        return new DefaultKafkaProducerFactory<>(config);
    }

    @Bean
    public KafkaTemplate<String, UrlCreatedEvent> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }

    @Bean
    public NewTopic urlCreatedTopic() {
        return TopicBuilder.name(topicName)
                .partitions(partitions)
                .replicas(replicas)
                .build();
    }
}
