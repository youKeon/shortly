package com.io.shortly.redirect.infrastructure.cache.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.io.shortly.redirect.infrastructure.cache.CachedRedirect;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

    @Bean
    public ReactiveRedisTemplate<String, CachedRedirect> reactiveRedisTemplate(
            ReactiveRedisConnectionFactory connectionFactory
    ) {
        // Custom ObjectMapper with JavaTimeModule for LocalDateTime support
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        // JSON serializer
        Jackson2JsonRedisSerializer<CachedRedirect> serializer =
                new Jackson2JsonRedisSerializer<>(objectMapper, CachedRedirect.class);

        // Serialization context
        RedisSerializationContext<String, CachedRedirect> serializationContext =
                RedisSerializationContext.<String, CachedRedirect>newSerializationContext()
                        .key(new StringRedisSerializer())
                        .value(serializer)
                        .hashKey(new StringRedisSerializer())
                        .hashValue(serializer)
                        .build();

        return new ReactiveRedisTemplate<>(connectionFactory, serializationContext);
    }
}
