package com.io.shortly.url.infrastructure.generator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NodeIdManagerTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private NodeIdManager nodeIdManager;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        nodeIdManager = new NodeIdManager(redisTemplate);
    }

    @Test
    @DisplayName("사용 가능한 Node ID를 획득한다")
    void init_AcquiresAvailableNodeId() {
        // given
        // 0번은 이미 점유됨 (false 반환)
        when(valueOperations.setIfAbsent(eq("snowflake:node:0"), anyString(), any(Duration.class)))
                .thenReturn(false);
        // 1번은 비어있음 (true 반환)
        when(valueOperations.setIfAbsent(eq("snowflake:node:1"), anyString(), any(Duration.class)))
                .thenReturn(true);

        // when
        nodeIdManager.init();

        // then
        assertThat(nodeIdManager.getWorkerId()).isEqualTo(1L); // 1 & 0x1F = 1
        assertThat(nodeIdManager.getDatacenterId()).isEqualTo(0L); // (1 >> 5) & 0x1F = 0
    }

    @Test
    @DisplayName("모든 Node ID가 점유된 경우 예외를 발생시킨다")
    void init_ThrowsExceptionWhenAllIdsTaken() {
        // given
        // 모든 ID에 대해 false 반환
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(false);

        // when & then
        assertThatThrownBy(() -> nodeIdManager.init())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to acquire any Node ID");
    }

    @Test
    @DisplayName("WorkerId와 DatacenterId를 올바르게 계산한다")
    void getIds_CalculatesCorrectly() {
        // given
        // ID 35 (Worker 3, Datacenter 1) -> 35 = 32*1 + 3
        // Binary: 00000 100011 (Datacenter=1, Worker=3)
        when(valueOperations.setIfAbsent(eq("snowflake:node:35"), anyString(), any(Duration.class)))
                .thenReturn(true);
        // 0~34는 false
        for (int i = 0; i < 35; i++) {
            when(valueOperations.setIfAbsent(eq("snowflake:node:" + i), anyString(), any(Duration.class)))
                    .thenReturn(false);
        }

        // when
        nodeIdManager.init();

        // then
        assertThat(nodeIdManager.getWorkerId()).isEqualTo(3L);
        assertThat(nodeIdManager.getDatacenterId()).isEqualTo(1L);
    }
}
