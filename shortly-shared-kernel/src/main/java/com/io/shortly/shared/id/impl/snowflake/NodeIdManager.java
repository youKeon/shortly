package com.io.shortly.shared.id.impl.snowflake;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NodeIdManager {

    private static final String NODE_ID_KEY_PREFIX = "snowflake:node:";
    private static final long LEASE_DURATION_SECONDS = 30;
    private static final long RENEW_INTERVAL_SECONDS = 10;
    private static final int MAX_NODE_ID = 1024;

    private final StringRedisTemplate redisTemplate;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    private int assignedNodeId = -1;

    @PostConstruct
    public void init() {
        assignNodeId();
        startHeartbeat();
    }

    private void assignNodeId() {
        String instanceId = UUID.randomUUID().toString();

        for (int i = 0; i < MAX_NODE_ID; i++) {
            String key = NODE_ID_KEY_PREFIX + i;
            Boolean success = redisTemplate.opsForValue()
                    .setIfAbsent(key, instanceId, Duration.ofSeconds(LEASE_DURATION_SECONDS));

            if (Boolean.TRUE.equals(success)) {
                this.assignedNodeId = i;
                log.info("Successfully acquired Node ID: {}", assignedNodeId);
                return;
            }
        }

        throw new IllegalStateException("Failed to acquire any Node ID. All " + MAX_NODE_ID + " IDs are taken.");
    }

    private void startHeartbeat() {
        scheduler.scheduleAtFixedRate(() -> {
            if (assignedNodeId == -1)
                return;

            String key = NODE_ID_KEY_PREFIX + assignedNodeId;
            try {
                Boolean renewed = redisTemplate.expire(key, Duration.ofSeconds(LEASE_DURATION_SECONDS));
                if (Boolean.FALSE.equals(renewed)) {
                    String instanceId = "reacquired-after-loss";
                    redisTemplate.opsForValue().setIfAbsent(key, instanceId,
                            Duration.ofSeconds(LEASE_DURATION_SECONDS));
                }
            } catch (Exception e) {
                log.error("Failed to renew Node ID lease", e);
            }
        }, RENEW_INTERVAL_SECONDS, RENEW_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    @PreDestroy
    public void cleanup() {
        scheduler.shutdown();
        if (assignedNodeId != -1) {
            String key = NODE_ID_KEY_PREFIX + assignedNodeId;
            redisTemplate.delete(key);
            log.info("Released Node ID: {}", assignedNodeId);
        }
    }

    public long getWorkerId() {
        if (assignedNodeId == -1)
            throw new IllegalStateException("Node ID not assigned yet");
        return assignedNodeId & 0x1F; // Lower 5 bits
    }

    public long getDatacenterId() {
        if (assignedNodeId == -1)
            throw new IllegalStateException("Node ID not assigned yet");
        return (assignedNodeId >> 5) & 0x1F; // Next 5 bits
    }
}
