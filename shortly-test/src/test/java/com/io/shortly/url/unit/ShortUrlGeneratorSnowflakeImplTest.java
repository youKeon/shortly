package com.io.shortly.url.unit;

import com.io.shortly.url.infrastructure.generator.ShortUrlGeneratorSnowflakeImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.io.shortly.url.infrastructure.generator.NodeIdManager;
import org.junit.jupiter.api.BeforeEach;

@DisplayName("ShortUrlGeneratorSnowflakeImpl 단위 테스트")
class ShortUrlGeneratorSnowflakeImplTest {

    private NodeIdManager nodeIdManager;

    @BeforeEach
    void setUp() {
        nodeIdManager = mock(NodeIdManager.class);
        when(nodeIdManager.getWorkerId()).thenReturn(0L);
        when(nodeIdManager.getDatacenterId()).thenReturn(0L);
    }

    @Nested
    @DisplayName("기본 생성 기능")
    class BasicGenerationTest {

        @Test
        @DisplayName("유효한 형식의 단축 코드를 생성한다")
        void generate_ReturnsValidFormat() {
            // given
            ShortUrlGeneratorSnowflakeImpl generator = new ShortUrlGeneratorSnowflakeImpl(nodeIdManager);
            String seed = "https://example.com";

            // when
            String shortCode = generator.generate(seed);

            // then
            assertThat(shortCode).isNotNull();
            assertThat(shortCode).matches("[0-9A-Za-z]+");
            assertThat(shortCode.length()).isGreaterThanOrEqualTo(6);
        }

        @Test
        @DisplayName("같은 seed라도 다른 코드를 생성한다 (timestamp 기반)")
        void generate_ProducesDifferentCodesForSameSeed() throws InterruptedException {
            // given
            ShortUrlGeneratorSnowflakeImpl generator = new ShortUrlGeneratorSnowflakeImpl(nodeIdManager);
            String seed = "https://example.com";

            // when
            String code1 = generator.generate(seed);
            Thread.sleep(1); // 시간 차이 보장
            String code2 = generator.generate(seed);

            // then
            assertThat(code1).isNotEqualTo(code2);
        }
    }

    @Nested
    @DisplayName("동시성 테스트")
    class ConcurrencyTest {

        @Test
        @DisplayName("멀티스레드 환경에서 중복 없이 고유한 코드를 생성한다")
        void generate_ProducesUniqueCodesInMultithreadedEnvironment() throws InterruptedException {
            // given
            ShortUrlGeneratorSnowflakeImpl generator = new ShortUrlGeneratorSnowflakeImpl(nodeIdManager);
            int threadCount = 10;
            int codesPerThread = 100;
            Set<String> generatedCodes = new HashSet<>();
            CountDownLatch latch = new CountDownLatch(threadCount);
            ExecutorService executorService = Executors.newFixedThreadPool(threadCount);

            // when
            for (int i = 0; i < threadCount; i++) {
                final int threadId = i;
                executorService.submit(() -> {
                    try {
                        for (int j = 0; j < codesPerThread; j++) {
                            String code = generator.generate("seed-" + threadId + "-" + j);
                            synchronized (generatedCodes) {
                                generatedCodes.add(code);
                            }
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await(10, TimeUnit.SECONDS);
            executorService.shutdown();

            // then
            int totalExpected = threadCount * codesPerThread;
            assertThat(generatedCodes).hasSize(totalExpected);
        }
    }

    @Nested
    @DisplayName("Snowflake ID 검증")
    class SnowflakeValidationTest {

        @Test
        @DisplayName("서로 다른 workerId는 다른 ID를 생성한다")
        void generate_ProducesDifferentIdsForDifferentWorkerIds() {
            // given
            NodeIdManager manager1 = mock(NodeIdManager.class);
            when(manager1.getWorkerId()).thenReturn(0L);
            when(manager1.getDatacenterId()).thenReturn(0L);

            NodeIdManager manager2 = mock(NodeIdManager.class);
            when(manager2.getWorkerId()).thenReturn(1L);
            when(manager2.getDatacenterId()).thenReturn(0L);

            ShortUrlGeneratorSnowflakeImpl generator1 = new ShortUrlGeneratorSnowflakeImpl(manager1);
            ShortUrlGeneratorSnowflakeImpl generator2 = new ShortUrlGeneratorSnowflakeImpl(manager2);
            String seed = "https://example.com";

            // when
            String code1 = generator1.generate(seed);
            String code2 = generator2.generate(seed);

            // then
            assertThat(code1).isNotEqualTo(code2);
        }
    }

    @Nested
    @DisplayName("Clock Drift 테스트")
    class ClockDriftTest {

        @Test
        @DisplayName("시간이 10ms 이내로 뒤로 가면 대기 후 성공한다")
        void generate_WaitsForSmallClockDrift() {
            // given
            NodeIdManager manager = mock(NodeIdManager.class);
            when(manager.getWorkerId()).thenReturn(0L);
            when(manager.getDatacenterId()).thenReturn(0L);

            ShortUrlGeneratorSnowflakeImpl generator = new ShortUrlGeneratorSnowflakeImpl(manager) {
                private long time = 1000L;
                private int callCount = 0;

                @Override
                protected long currentTimeMillis() {
                    callCount++;
                    if (callCount == 2) {
                        return 995L; // 5ms backwards
                    }
                    if (callCount == 3) {
                        return 1001L; // Recovered
                    }
                    return time++;
                }
            };

            // when
            generator.generate("seed"); // First call (time=1000)
            String code = generator.generate("seed"); // Second call (time=995 -> wait -> 1001)

            // then
            assertThat(code).isNotNull();
        }

        @Test
        @DisplayName("시간이 10ms 초과하여 뒤로 가면 예외를 발생시킨다")
        void generate_ThrowsExceptionForLargeClockDrift() {
            // given
            NodeIdManager manager = mock(NodeIdManager.class);
            when(manager.getWorkerId()).thenReturn(0L);
            when(manager.getDatacenterId()).thenReturn(0L);

            ShortUrlGeneratorSnowflakeImpl generator = new ShortUrlGeneratorSnowflakeImpl(manager) {
                private long time = 1000L;
                private int callCount = 0;

                @Override
                protected long currentTimeMillis() {
                    callCount++;
                    if (callCount == 2) {
                        return 900L; // 100ms backwards
                    }
                    return time++;
                }
            };

            // when
            generator.generate("seed"); // First call

            // then
            assertThatThrownBy(() -> generator.generate("seed"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Clock moved backwards");
        }
    }
}
