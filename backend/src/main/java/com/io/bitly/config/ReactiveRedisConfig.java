package com.io.bitly.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.io.bitly.application.dto.ShortenUrlResult.ShortUrlLookupResult;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
@Profile("phase4")
public class ReactiveRedisConfig {

    @Bean
    public ReactiveRedisTemplate<String, ShortUrlLookupResult> reactiveRedisTemplate(
            ReactiveRedisConnectionFactory factory, ObjectMapper objectMapper) {
        Jackson2JsonRedisSerializer<ShortUrlLookupResult> serializer =
                new Jackson2JsonRedisSerializer<>(objectMapper, ShortUrlLookupResult.class);

        RedisSerializationContext<String, ShortUrlLookupResult> serializationContext =
                RedisSerializationContext.<String, ShortUrlLookupResult>newSerializationContext(new StringRedisSerializer())
                        .value(serializer)
                        .build();
        return new ReactiveRedisTemplate<>(factory, serializationContext);
    }

    @Bean
    public ReactiveStringRedisTemplate reactiveStringRedisTemplate(ReactiveRedisConnectionFactory factory) {
        return new ReactiveStringRedisTemplate(factory);
    }
}

