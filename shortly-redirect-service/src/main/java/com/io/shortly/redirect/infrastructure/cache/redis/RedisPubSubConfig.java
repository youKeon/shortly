package com.io.shortly.redirect.infrastructure.cache.redis;

import com.io.shortly.redirect.infrastructure.cache.pubsub.CacheNotificationSubscriber;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;

@Configuration
@ConditionalOnProperty(
    prefix = "shortly.cache.sync",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class RedisPubSubConfig {

    public static final String CACHE_NOTIFICATION_CHANNEL = "cache:notify:create-url";

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            MessageListenerAdapter listenerAdapter
    ) {

        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(listenerAdapter, new PatternTopic(CACHE_NOTIFICATION_CHANNEL));

        return container;
    }

    @Bean
    public MessageListenerAdapter messageListenerAdapter(CacheNotificationSubscriber subscriber) {
        return new MessageListenerAdapter(subscriber, "onUrlCreated");
    }
}
