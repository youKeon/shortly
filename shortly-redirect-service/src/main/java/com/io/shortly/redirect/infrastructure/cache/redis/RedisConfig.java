package com.io.shortly.redirect.infrastructure.cache.redis;

import com.fasterxml.jackson.databind.DeserializationFeature;
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

        template.setEnableTransactionSupport(false);

        // String 직렬화
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());

        // JSON 직렬화
        ObjectMapper objectMapper = createObjectMapper();

        Jackson2JsonRedisSerializer<CachedRedirect> serializer =
            new Jackson2JsonRedisSerializer<>(objectMapper, CachedRedirect.class);
        template.setValueSerializer(serializer);
        template.setHashValueSerializer(serializer);

        template.afterPropertiesSet();

        return template;
    }

    private ObjectMapper createObjectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();

        // 필수 모듈
        objectMapper.registerModule(new JavaTimeModule());

        // 성능 최적화 설정
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        objectMapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        objectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

        // 불필요한 기능 비활성화
        objectMapper.disable(SerializationFeature.INDENT_OUTPUT);
        objectMapper.disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE);

        return objectMapper;
    }
}
