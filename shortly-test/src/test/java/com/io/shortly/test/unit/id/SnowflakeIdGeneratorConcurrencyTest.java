package com.io.shortly.test.unit.id;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.io.shortly.shared.id.impl.snowflake.NodeIdManager;
import com.io.shortly.shared.id.impl.snowflake.UniqueIdGeneratorSnowflakeImpl;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Snowflake ID 생성기 대량 요청 중복 방지 테스트")
class SnowflakeIdGeneratorConcurrencyTest {

    private UniqueIdGeneratorSnowflakeImpl generator;

    @BeforeEach
    void setUp() {
        NodeIdManager nodeIdManager = new NodeIdManager(null) {
            @Override
            public long getWorkerId() {
                return 1L;
            }

            @Override
            public long getDatacenterId() {
                return 1L;
            }
        };
        generator = new UniqueIdGeneratorSnowflakeImpl(nodeIdManager);
    }

    @Test
    @DisplayName("단일 스레드 - 10만개 ID 생성 시 중복 없음")
    void singleThread_100K_NoDuplicates() {
        // given
        int count = 100_000;
        Set<Long> generatedIds = new HashSet<>();

        // when
        for (int i = 0; i < count; i++) {
            long id = generator.generate();
            generatedIds.add(id);
        }

        // then
        assertEquals(count, generatedIds.size(), "생성된 ID는 모두 고유해야 함");
    }

    @Test
    @DisplayName("단일 스레드 - 생성된 ID는 항상 증가함")
    void singleThread_MonotonicallyIncreasing() {
        // given
        int count = 10_000;
        List<Long> generatedIds = new ArrayList<>();

        // when
        for (int i = 0; i < count; i++) {
            generatedIds.add(generator.generate());
        }

        // then
        for (int i = 1; i < generatedIds.size(); i++) {
            assertTrue(
                generatedIds.get(i) > generatedIds.get(i - 1),
                "ID는 항상 이전보다 커야 함 (단조 증가)"
            );
        }
    }

    @Test
    @DisplayName("멀티 스레드 - 10개 스레드에서 각 1만개씩 동시 생성 시 중복 없음")
    void multiThread_10Threads_10K_Each_NoDuplicates() throws InterruptedException {
        // given
        int threadCount = 10;
        int idsPerThread = 10_000;
        Set<Long> allIds = ConcurrentHashMap.newKeySet();
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        // when
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await(); // 모든 스레드가 동시에 시작하도록 대기
                    for (int j = 0; j < idsPerThread; j++) {
                        long id = generator.generate();
                        allIds.add(id);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // 모든 스레드 동시 시작
        boolean finished = doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        // then
        assertTrue(finished, "모든 스레드가 30초 내에 완료되어야 함");
        assertEquals(threadCount * idsPerThread, allIds.size(),
            "생성된 모든 ID는 고유해야 함 (중복 없음)");
    }

    @Test
    @DisplayName("멀티 스레드 - 20개 스레드 동시 생성 시 중복 없음")
    void multiThread_20Threads_NoDuplicates() throws InterruptedException {
        // given
        int threadCount = 20;
        int idsPerThread = 5_000;
        Set<Long> allIds = ConcurrentHashMap.newKeySet();
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        // when
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int j = 0; j < idsPerThread; j++) {
                        long id = generator.generate();
                        allIds.add(id);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean finished = doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        // then
        assertTrue(finished, "모든 스레드가 30초 내에 완료되어야 함");
        assertEquals(threadCount * idsPerThread, allIds.size(),
            "20개 스레드에서 생성한 모든 ID는 고유해야 함");
    }

    @Test
    @DisplayName("순간 대량 생성 - 같은 밀리초에 최대 4096개 생성 가능")
    void burstGeneration_SameMillisecond_MaxSequence() {
        // given
        int maxSequence = 4096; // 2^12 (SEQUENCE_BITS = 12)
        Set<Long> generatedIds = new HashSet<>();

        // when - 매우 빠르게 연속 생성하여 같은 밀리초 내에서 생성
        long startTime = System.currentTimeMillis();
        for (int i = 0; i < maxSequence; i++) {
            long id = generator.generate();
            generatedIds.add(id);
        }
        long endTime = System.currentTimeMillis();

        // then
        assertEquals(maxSequence, generatedIds.size(),
            "같은 밀리초 내에서 4096개의 고유한 ID 생성 가능");

        // 같은 밀리초 또는 매우 짧은 시간 내에 생성되었는지 확인
        long duration = endTime - startTime;
        assertTrue(duration < 100,
            "4096개 ID는 100ms 이내에 생성되어야 함 (실제: " + duration + "ms)");
    }

    @Test
    @DisplayName("성능 테스트 - 100만개 ID를 10초 이내에 생성 (100K TPS 이상)")
    void performance_1M_IDs_Within10Seconds() {
        // given
        int count = 1_000_000;
        Set<Long> generatedIds = new HashSet<>();

        // when
        long startTime = System.currentTimeMillis();
        for (int i = 0; i < count; i++) {
            long id = generator.generate();
            generatedIds.add(id);
        }
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        // then
        assertEquals(count, generatedIds.size(), "100만개 모두 고유해야 함");
        assertTrue(duration < 10_000,
            "100만개 ID는 10초 이내에 생성되어야 함 (실제: " + duration + "ms)");

        double tps = (count * 1000.0) / duration;
        System.out.printf("성능: %,.0f TPS (Duration: %,dms)%n", tps, duration);
        assertTrue(tps > 100_000,
            "처리량은 100K TPS 이상이어야 함 (실제: " + String.format("%.0f", tps) + " TPS)");
    }

    @Test
    @DisplayName("멀티 스레드 성능 - 10개 스레드에서 총 100만개 생성")
    void multiThreadPerformance_10Threads_1M_Total() throws InterruptedException {
        // given
        int threadCount = 10;
        int totalCount = 1_000_000;
        int idsPerThread = totalCount / threadCount;
        Set<Long> allIds = ConcurrentHashMap.newKeySet();
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        // when
        long startTime = System.currentTimeMillis();
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int j = 0; j < idsPerThread; j++) {
                        long id = generator.generate();
                        allIds.add(id);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean finished = doneLatch.await(30, TimeUnit.SECONDS);
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        executor.shutdown();

        // then
        assertTrue(finished, "모든 스레드가 30초 내에 완료되어야 함");
        assertEquals(totalCount, allIds.size(), "100만개 모두 고유해야 함");

        double tps = (totalCount * 1000.0) / duration;
        System.out.printf("멀티스레드 성능: %,.0f TPS (Duration: %,dms)%n", tps, duration);
    }

    @Test
    @DisplayName("서로 다른 Worker/Datacenter ID는 다른 ID 생성")
    void differentWorkers_GenerateDifferentIds() {
        // given
        NodeIdManager nodeManager1 = new NodeIdManager(null) {
            @Override
            public long getWorkerId() {
                return 1L;
            }

            @Override
            public long getDatacenterId() {
                return 1L;
            }
        };

        NodeIdManager nodeManager2 = new NodeIdManager(null) {
            @Override
            public long getWorkerId() {
                return 2L;
            }

            @Override
            public long getDatacenterId() {
                return 1L;
            }
        };

        UniqueIdGeneratorSnowflakeImpl generator1 =
            new UniqueIdGeneratorSnowflakeImpl(nodeManager1);
        UniqueIdGeneratorSnowflakeImpl generator2 =
            new UniqueIdGeneratorSnowflakeImpl(nodeManager2);

        Set<Long> allIds = new HashSet<>();

        // when - 두 생성기에서 각각 1만개씩 생성
        for (int i = 0; i < 10_000; i++) {
            allIds.add(generator1.generate());
            allIds.add(generator2.generate());
        }

        // then
        assertEquals(20_000, allIds.size(),
            "서로 다른 워커의 ID는 모두 고유해야 함");
    }
}
