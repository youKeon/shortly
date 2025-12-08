package com.io.shortly.benchmark;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 빠른 성능 비교 테스트 (JMH 없이)
 */
public class QuickPerformanceTest {

    public static void main(String[] args) {
        System.out.println("=".repeat(80));
        System.out.println("Snowflake ID vs UUID - 빠른 성능 비교");
        System.out.println("=".repeat(80));

        // 1. 생성 시간 비교
        testGenerationTime();

        // 2. 메모리 사용량 비교
        testMemoryUsage();

        // 3. 실제 사용 시나리오 비교
        testRealWorldScenario();

        System.out.println("\n" + "=".repeat(80));
        System.out.println("테스트 완료");
        System.out.println("=".repeat(80));
    }

    private static void testGenerationTime() {
        System.out.println("\n[1] ID 생성 시간 비교 (1,000,000회)");
        System.out.println("-".repeat(80));

        EventIdGenerationBenchmark.SnowflakeIdGenerator snowflake =
            new EventIdGenerationBenchmark.SnowflakeIdGenerator(1, 1);

        // UUID 생성 시간
        long startUuid = System.nanoTime();
        for (int i = 0; i < 1_000_000; i++) {
            String id = UUID.randomUUID().toString();
        }
        long uuidTime = System.nanoTime() - startUuid;

        // Snowflake Long 생성 시간
        long startSnowflakeLong = System.nanoTime();
        for (int i = 0; i < 1_000_000; i++) {
            long id = snowflake.nextId();
        }
        long snowflakeLongTime = System.nanoTime() - startSnowflakeLong;

        // Snowflake String 생성 시간
        long startSnowflakeStr = System.nanoTime();
        for (int i = 0; i < 1_000_000; i++) {
            long id = snowflake.nextId();
            String str = EventIdGenerationBenchmark.Base62.encode(id);
        }
        long snowflakeStrTime = System.nanoTime() - startSnowflakeStr;

        // 결과 출력
        System.out.printf("UUID String:            %,15d ns (%,8.2f ms)\n",
            uuidTime, uuidTime / 1_000_000.0);
        System.out.printf("Snowflake Long:         %,15d ns (%,8.2f ms) - %.1fx faster\n",
            snowflakeLongTime, snowflakeLongTime / 1_000_000.0,
            (double) uuidTime / snowflakeLongTime);
        System.out.printf("Snowflake String:       %,15d ns (%,8.2f ms) - %.1fx faster\n",
            snowflakeStrTime, snowflakeStrTime / 1_000_000.0,
            (double) uuidTime / snowflakeStrTime);
    }

    private static void testMemoryUsage() {
        System.out.println("\n[2] 메모리 사용량 비교 (100,000개 저장)");
        System.out.println("-".repeat(80));

        Runtime runtime = Runtime.getRuntime();
        EventIdGenerationBenchmark.SnowflakeIdGenerator snowflake =
            new EventIdGenerationBenchmark.SnowflakeIdGenerator(1, 1);

        // GC 실행
        System.gc();
        try { Thread.sleep(100); } catch (InterruptedException e) {}

        // UUID String 메모리
        long beforeUuid = runtime.totalMemory() - runtime.freeMemory();
        Set<String> uuidSet = new HashSet<>();
        for (int i = 0; i < 100_000; i++) {
            uuidSet.add(UUID.randomUUID().toString());
        }
        long afterUuid = runtime.totalMemory() - runtime.freeMemory();
        long uuidMemory = afterUuid - beforeUuid;

        System.gc();
        try { Thread.sleep(100); } catch (InterruptedException e) {}

        // Snowflake Long 메모리
        long beforeSnowflakeLong = runtime.totalMemory() - runtime.freeMemory();
        Set<Long> snowflakeLongSet = new HashSet<>();
        for (int i = 0; i < 100_000; i++) {
            snowflakeLongSet.add(snowflake.nextId());
        }
        long afterSnowflakeLong = runtime.totalMemory() - runtime.freeMemory();
        long snowflakeLongMemory = afterSnowflakeLong - beforeSnowflakeLong;

        System.gc();
        try { Thread.sleep(100); } catch (InterruptedException e) {}

        // Snowflake String 메모리
        long beforeSnowflakeStr = runtime.totalMemory() - runtime.freeMemory();
        Set<String> snowflakeStrSet = new HashSet<>();
        for (int i = 0; i < 100_000; i++) {
            snowflakeStrSet.add(EventIdGenerationBenchmark.Base62.encode(snowflake.nextId()));
        }
        long afterSnowflakeStr = runtime.totalMemory() - runtime.freeMemory();
        long snowflakeStrMemory = afterSnowflakeStr - beforeSnowflakeStr;

        // 결과 출력
        System.out.printf("UUID String:            %,12d bytes (%,8.2f MB)\n",
            uuidMemory, uuidMemory / 1_048_576.0);
        System.out.printf("Snowflake Long:         %,12d bytes (%,8.2f MB) - %.1fx smaller\n",
            snowflakeLongMemory, snowflakeLongMemory / 1_048_576.0,
            (double) uuidMemory / snowflakeLongMemory);
        System.out.printf("Snowflake String:       %,12d bytes (%,8.2f MB) - %.1fx smaller\n",
            snowflakeStrMemory, snowflakeStrMemory / 1_048_576.0,
            (double) uuidMemory / snowflakeStrMemory);

        System.out.printf("\n객체당 평균 메모리:\n");
        System.out.printf("UUID String:            %,8d bytes/object\n", uuidMemory / 100_000);
        System.out.printf("Snowflake Long:         %,8d bytes/object\n", snowflakeLongMemory / 100_000);
        System.out.printf("Snowflake String:       %,8d bytes/object\n", snowflakeStrMemory / 100_000);
    }

    private static void testRealWorldScenario() {
        System.out.println("\n[3] 실제 시나리오 비교 (17,000 TPS 시뮬레이션, 1초)");
        System.out.println("-".repeat(80));

        EventIdGenerationBenchmark.SnowflakeIdGenerator snowflake =
            new EventIdGenerationBenchmark.SnowflakeIdGenerator(1, 1);

        int tps = 17_000; // 17K TPS 목표

        // UUID 방식
        long startUuid = System.nanoTime();
        for (int i = 0; i < tps; i++) {
            String eventId = UUID.randomUUID().toString();
            EventIdMemoryBenchmark.MockEvent event = new EventIdMemoryBenchmark.MockEvent(
                eventId,
                "testCode",
                "https://example.com/path"
            );
        }
        long uuidTime = System.nanoTime() - startUuid;

        // Snowflake Long 방식
        long startSnowflakeLong = System.nanoTime();
        for (int i = 0; i < tps; i++) {
            long eventId = snowflake.nextId();
            EventIdMemoryBenchmark.MockEventLong event = new EventIdMemoryBenchmark.MockEventLong(
                eventId,
                "testCode",
                "https://example.com/path"
            );
        }
        long snowflakeLongTime = System.nanoTime() - startSnowflakeLong;

        // 결과 출력
        System.out.printf("UUID 방식:              %,15d ns (%,8.2f ms)\n",
            uuidTime, uuidTime / 1_000_000.0);
        System.out.printf("Snowflake Long 방식:    %,15d ns (%,8.2f ms) - %.1fx faster\n",
            snowflakeLongTime, snowflakeLongTime / 1_000_000.0,
            (double) uuidTime / snowflakeLongTime);

        System.out.printf("\n초당 처리 가능 이벤트 수 (1초 기준):\n");
        long uuidTps = 1_000_000_000L / (uuidTime / tps);
        long snowflakeTps = 1_000_000_000L / (snowflakeLongTime / tps);
        System.out.printf("UUID 방식:              %,12d events/sec\n", uuidTps);
        System.out.printf("Snowflake Long 방식:    %,12d events/sec (%.1fx 여유)\n",
            snowflakeTps, (double) snowflakeTps / 17_000);

        // 17K TPS에서의 여유 시간
        double uuidMargin = ((double) uuidTps / 17_000 - 1) * 100;
        double snowflakeMargin = ((double) snowflakeTps / 17_000 - 1) * 100;
        System.out.printf("\n17K TPS 처리 여유:\n");
        System.out.printf("UUID 방식:              %.1f%% 여유\n", uuidMargin);
        System.out.printf("Snowflake Long 방식:    %.1f%% 여유\n", snowflakeMargin);
    }
}
