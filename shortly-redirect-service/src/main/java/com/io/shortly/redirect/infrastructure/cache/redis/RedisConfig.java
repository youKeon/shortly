package com.io.shortly.redirect.infrastructure.cache.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.io.shortly.redirect.infrastructure.cache.CachedRedirect;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, CachedRedirect> redisTemplate(
        RedisConnectionFactory connectionFactory
    ) {
        RedisTemplate<String, CachedRedirect> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // Key: String 직렬화
        template.setKeySerializer(new StringRedisSerializer());

        // Value: JSON 직렬화 (Java 8 날짜/시간 지원)
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        Jackson2JsonRedisSerializer<CachedRedirect> serializer =
            new Jackson2JsonRedisSerializer<>(objectMapper, CachedRedirect.class);
        template.setValueSerializer(serializer);

        return template;
    }
}
