package com.io.shortly.benchmark;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 간단한 벤치마크 (JMH 없이)
 * JMH보다 정확도는 낮지만 빠르게 결과 확인 가능
 */
public class SimpleBenchmarkRunner {

    private static final int WARMUP_ITERATIONS = 5;
    private static final int MEASUREMENT_ITERATIONS = 10;
    private static final int OPERATIONS_PER_ITERATION = 1_000_000;

    public static void main(String[] args) {
        System.out.println("=".repeat(90));
        System.out.println("Snowflake ID vs UUID - 성능 벤치마크");
        System.out.println("Warmup: " + WARMUP_ITERATIONS + " iterations");
        System.out.println("Measurement: " + MEASUREMENT_ITERATIONS + " iterations");
        System.out.println("Operations: " + String.format("%,d", OPERATIONS_PER_ITERATION) + " per iteration");
        System.out.println("=".repeat(90));

        // 1. ID 생성 시간 비교
        benchmarkIdGeneration();

        // 2. 메모리 할당 비교
        benchmarkMemoryAllocation();

        System.out.println("\n" + "=".repeat(90));
        System.out.println("벤치마크 완료");
        System.out.println("=".repeat(90));
    }

    private static void benchmarkIdGeneration() {
        System.out.println("\n[벤치마크 1] ID 생성 시간 비교");
        System.out.println("-".repeat(90));

        EventIdGenerationBenchmark.SnowflakeIdGenerator snowflake =
            new EventIdGenerationBenchmark.SnowflakeIdGenerator(1, 1);

        // Warmup
        System.out.println("Warming up...");
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            warmupUUID();
            warmupSnowflakeLong(snowflake);
            warmupSnowflakeString(snowflake);
        }

        // Measurement - UUID
        System.out.println("Measuring UUID generation...");
        List<Long> uuidTimes = new ArrayList<>();
        for (int i = 0; i < MEASUREMENT_ITERATIONS; i++) {
            long time = measureUUID();
            uuidTimes.add(time);
        }

        // Measurement - Snowflake Long
        System.out.println("Measuring Snowflake Long generation...");
        List<Long> snowflakeLongTimes = new ArrayList<>();
        for (int i = 0; i < MEASUREMENT_ITERATIONS; i++) {
            long time = measureSnowflakeLong(snowflake);
            snowflakeLongTimes.add(time);
        }

        // Measurement - Snowflake String
        System.out.println("Measuring Snowflake String generation...");
        List<Long> snowflakeStringTimes = new ArrayList<>();
        for (int i = 0; i < MEASUREMENT_ITERATIONS; i++) {
            long time = measureSnowflakeString(snowflake);
            snowflakeStringTimes.add(time);
        }

        // 통계 계산
        Statistics uuidStats = new Statistics(uuidTimes);
        Statistics snowflakeLongStats = new Statistics(snowflakeLongTimes);
        Statistics snowflakeStringStats = new Statistics(snowflakeStringTimes);

        // 결과 출력
        System.out.println("\n결과 (시간: 낮을수록 좋음):");
        System.out.println("-".repeat(90));
        printBenchmarkResult("UUID String", uuidStats, 1.0);
        printBenchmarkResult("Snowflake Long",
            snowflakeLongStats,
            (double) uuidStats.mean / snowflakeLongStats.mean);
        printBenchmarkResult("Snowflake String",
            snowflakeStringStats,
            (double) uuidStats.mean / snowflakeStringStats.mean);

        // 처리량 계산
        System.out.println("\n처리량 (높을수록 좋음):");
        System.out.println("-".repeat(90));
        printThroughput("UUID String", uuidStats);
        printThroughput("Snowflake Long", snowflakeLongStats);
        printThroughput("Snowflake String", snowflakeStringStats);
    }

    private static void benchmarkMemoryAllocation() {
        System.out.println("\n[벤치마크 2] 메모리 할당 비교");
        System.out.println("-".repeat(90));

        Runtime runtime = Runtime.getRuntime();
        EventIdGenerationBenchmark.SnowflakeIdGenerator snowflake =
            new EventIdGenerationBenchmark.SnowflakeIdGenerator(1, 1);

        // UUID 메모리 측정
        System.gc();
        sleep(200);
        long uuidMemory = measureMemoryUUID(runtime);

        // Snowflake Long 메모리 측정
        System.gc();
        sleep(200);
        long snowflakeLongMemory = measureMemorySnowflakeLong(runtime, snowflake);

        // Snowflake String 메모리 측정
        System.gc();
        sleep(200);
        long snowflakeStringMemory = measureMemorySnowflakeString(runtime, snowflake);

        // 결과 출력
        System.out.println("\n결과 (메모리: 낮을수록 좋음):");
        System.out.println("-".repeat(90));
        printMemoryResult("UUID String", uuidMemory, 1.0);
        printMemoryResult("Snowflake Long",
            snowflakeLongMemory,
            (double) uuidMemory / snowflakeLongMemory);
        printMemoryResult("Snowflake String",
            snowflakeStringMemory,
            (double) uuidMemory / snowflakeStringMemory);
    }

    // Warmup methods
    private static void warmupUUID() {
        for (int i = 0; i < OPERATIONS_PER_ITERATION / 10; i++) {
            UUID.randomUUID().toString();
        }
    }

    private static void warmupSnowflakeLong(EventIdGenerationBenchmark.SnowflakeIdGenerator gen) {
        for (int i = 0; i < OPERATIONS_PER_ITERATION / 10; i++) {
            gen.nextId();
        }
    }

    private static void warmupSnowflakeString(EventIdGenerationBenchmark.SnowflakeIdGenerator gen) {
        for (int i = 0; i < OPERATIONS_PER_ITERATION / 10; i++) {
            EventIdGenerationBenchmark.Base62.encode(gen.nextId());
        }
    }

    // Measurement methods
    private static long measureUUID() {
        long start = System.nanoTime();
        for (int i = 0; i < OPERATIONS_PER_ITERATION; i++) {
            String id = UUID.randomUUID().toString();
        }
        return System.nanoTime() - start;
    }

    private static long measureSnowflakeLong(EventIdGenerationBenchmark.SnowflakeIdGenerator gen) {
        long start = System.nanoTime();
        for (int i = 0; i < OPERATIONS_PER_ITERATION; i++) {
            long id = gen.nextId();
        }
        return System.nanoTime() - start;
    }

    private static long measureSnowflakeString(EventIdGenerationBenchmark.SnowflakeIdGenerator gen) {
        long start = System.nanoTime();
        for (int i = 0; i < OPERATIONS_PER_ITERATION; i++) {
            String id = EventIdGenerationBenchmark.Base62.encode(gen.nextId());
        }
        return System.nanoTime() - start;
    }

    // Memory measurement methods
    private static long measureMemoryUUID(Runtime runtime) {
        long before = runtime.totalMemory() - runtime.freeMemory();
        String[] ids = new String[100_000];
        for (int i = 0; i < ids.length; i++) {
            ids[i] = UUID.randomUUID().toString();
        }
        long after = runtime.totalMemory() - runtime.freeMemory();
        return after - before;
    }

    private static long measureMemorySnowflakeLong(Runtime runtime,
                                                    EventIdGenerationBenchmark.SnowflakeIdGenerator gen) {
        long before = runtime.totalMemory() - runtime.freeMemory();
        long[] ids = new long[100_000];
        for (int i = 0; i < ids.length; i++) {
            ids[i] = gen.nextId();
        }
        long after = runtime.totalMemory() - runtime.freeMemory();
        return after - before;
    }

    private static long measureMemorySnowflakeString(Runtime runtime,
                                                      EventIdGenerationBenchmark.SnowflakeIdGenerator gen) {
        long before = runtime.totalMemory() - runtime.freeMemory();
        String[] ids = new String[100_000];
        for (int i = 0; i < ids.length; i++) {
            ids[i] = EventIdGenerationBenchmark.Base62.encode(gen.nextId());
        }
        long after = runtime.totalMemory() - runtime.freeMemory();
        return after - before;
    }

    // Utility methods
    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void printBenchmarkResult(String name, Statistics stats, double improvement) {
        double avgTimeUs = stats.mean / 1_000.0 / OPERATIONS_PER_ITERATION;
        System.out.printf("%-20s: %8.3f ± %6.3f us/op",
            name, avgTimeUs, stats.stddev / 1_000.0 / OPERATIONS_PER_ITERATION);
        if (improvement > 1.0) {
            System.out.printf("  [%.2fx faster]", improvement);
        }
        System.out.println();
    }

    private static void printThroughput(String name, Statistics stats) {
        double throughput = (double) OPERATIONS_PER_ITERATION * 1_000_000_000.0 / stats.mean;
        System.out.printf("%-20s: %,15.0f ops/sec\n", name, throughput);
    }

    private static void printMemoryResult(String name, long bytes, double improvement) {
        double bytesPerOp = bytes / 100_000.0;
        System.out.printf("%-20s: %8.2f MB total, %6.1f bytes/op",
            name, bytes / 1_048_576.0, bytesPerOp);
        if (improvement > 1.0) {
            System.out.printf("  [%.2fx smaller]", improvement);
        }
        System.out.println();
    }

    // Statistics helper class
    static class Statistics {
        final long mean;
        final long min;
        final long max;
        final double stddev;

        Statistics(List<Long> values) {
            this.mean = values.stream().mapToLong(Long::longValue).sum() / values.size();
            this.min = values.stream().mapToLong(Long::longValue).min().orElse(0);
            this.max = values.stream().mapToLong(Long::longValue).max().orElse(0);

            double variance = values.stream()
                .mapToDouble(v -> Math.pow(v - mean, 2))
                .average()
                .orElse(0.0);
            this.stddev = Math.sqrt(variance);
        }
    }
}
