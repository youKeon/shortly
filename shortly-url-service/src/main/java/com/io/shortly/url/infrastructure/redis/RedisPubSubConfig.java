package com.io.shortly.url.infrastructure.redis;

import com.io.shortly.shared.event.UrlCreatedEvent;
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
            RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, UrlCreatedEvent> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // Key: String serializer
        template.setKeySerializer(new StringRedisSerializer());

        // Value: JSON serializer for UrlCreatedEvent
        Jackson2JsonRedisSerializer<UrlCreatedEvent> serializer =
                new Jackson2JsonRedisSerializer<>(UrlCreatedEvent.class);
        template.setValueSerializer(serializer);

        return template;
    }
}
