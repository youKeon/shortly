package com.io.shortly.benchmark;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Snowflake ID vs UUID 성능 비교 벤치마크
 *
 * 측정 항목:
 * 1. ID 생성 시간 (Throughput, Average Time)
 * 2. 메모리 할당량 (GC Allocation Rate)
 */
@BenchmarkMode({Mode.Throughput, Mode.AverageTime})
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 3, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1, jvmArgsAppend = {
    "-Xmx2g",
    "-Xms2g",
    "-XX:+UseG1GC",
    "-XX:+PrintGCDetails"
})
@State(Scope.Benchmark)
public class EventIdGenerationBenchmark {

    private SnowflakeIdGenerator snowflakeGenerator;

    @Setup
    public void setup() {
        // Snowflake Generator 초기화 (Worker ID: 1, Datacenter ID: 1)
        snowflakeGenerator = new SnowflakeIdGenerator(1, 1);
    }

    /**
     * UUID 생성 + String 변환 벤치마크
     * 현재 BaseEvent에서 사용 중인 방식
     */
    @Benchmark
    public void generateUUID(Blackhole blackhole) {
        String eventId = UUID.randomUUID().toString();
        blackhole.consume(eventId);
    }

    /**
     * Snowflake Long ID 생성 벤치마크
     * 제안하는 방식 (Long 타입)
     */
    @Benchmark
    public void generateSnowflakeLong(Blackhole blackhole) {
        long eventId = snowflakeGenerator.nextId();
        blackhole.consume(eventId);
    }

    /**
     * Snowflake ID 생성 + Base62 인코딩 벤치마크
     * 제안하는 방식 (String 타입 유지)
     */
    @Benchmark
    public void generateSnowflakeString(Blackhole blackhole) {
        long id = snowflakeGenerator.nextId();
        String eventId = Base62.encode(id);
        blackhole.consume(eventId);
    }

    /**
     * 간단한 Snowflake ID Generator 구현
     */
    public static class SnowflakeIdGenerator {
        private static final long CUSTOM_EPOCH = 1704067200000L; // 2024-01-01

        private static final long WORKER_ID_BITS = 5L;
        private static final long DATACENTER_ID_BITS = 5L;
        private static final long SEQUENCE_BITS = 12L;

        private static final long SEQUENCE_MASK = ~(-1L << SEQUENCE_BITS);

        private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;
        private static final long DATACENTER_ID_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;
        private static final long TIMESTAMP_LEFT_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS + DATACENTER_ID_BITS;

        private final long workerId;
        private final long datacenterId;

        private long sequence = 0L;
        private long lastTimestamp = -1L;

        public SnowflakeIdGenerator(long workerId, long datacenterId) {
            this.workerId = workerId;
            this.datacenterId = datacenterId;
        }

        public synchronized long nextId() {
            long timestamp = System.currentTimeMillis();

            if (timestamp < lastTimestamp) {
                throw new IllegalStateException("Clock moved backwards");
            }

            if (timestamp == lastTimestamp) {
                sequence = (sequence + 1) & SEQUENCE_MASK;
                if (sequence == 0) {
                    timestamp = waitNextMillis(lastTimestamp);
                }
            } else {
                sequence = 0L;
            }

            lastTimestamp = timestamp;

            return ((timestamp - CUSTOM_EPOCH) << TIMESTAMP_LEFT_SHIFT)
                    | (datacenterId << DATACENTER_ID_SHIFT)
                    | (workerId << WORKER_ID_SHIFT)
                    | sequence;
        }

        private long waitNextMillis(long lastTimestamp) {
            long timestamp = System.currentTimeMillis();
            while (timestamp <= lastTimestamp) {
                timestamp = System.currentTimeMillis();
            }
            return timestamp;
        }
    }

    /**
     * Base62 인코더
     */
    public static class Base62 {
        private static final char[] DIGITS =
            "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray();
        private static final int BASE = DIGITS.length;

        public static String encode(long value) {
            if (value == 0) {
                return "0";
            }

            StringBuilder sb = new StringBuilder();
            long current = value;

            while (current > 0) {
                sb.append(DIGITS[(int) (current % BASE)]);
                current /= BASE;
            }

            return sb.reverse().toString();
        }
    }
}
