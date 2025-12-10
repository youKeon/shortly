package com.io.shortly.url.infrastructure.event.redis;

import com.io.shortly.shared.event.UrlCreatedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
@RequiredArgsConstructor
public class RedisPubSubConfig {

    @Bean
    public RedisTemplate<String, UrlCreatedEvent> urlCreatedEventRedisTemplate(
            RedisConnectionFactory connectionFactory
    ) {
        RedisTemplate<String, UrlCreatedEvent> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        Jackson2JsonRedisSerializer<UrlCreatedEvent> serializer = new Jackson2JsonRedisSerializer<>(
            objectMapper,
            UrlCreatedEvent.class
        );
        template.setValueSerializer(serializer);

        return template;
    }
}
