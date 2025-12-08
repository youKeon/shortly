package com.io.shortly.benchmark;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Event ID 메모리 사용량 비교 벤치마크
 *
 * 측정 항목:
 * 1. GC Allocation Rate (초당 메모리 할당량)
 * 2. 객체 크기 비교
 */
@BenchmarkMode({Mode.Throughput})
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 5, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1, jvmArgsAppend = {
    "-Xmx2g",
    "-Xms2g",
    "-XX:+UseG1GC",
    "-verbose:gc",
    "-XX:+PrintGCDetails",
    "-XX:+PrintGCTimeStamps"
})
@State(Scope.Thread)
public class EventIdMemoryBenchmark {

    private EventIdGenerationBenchmark.SnowflakeIdGenerator snowflakeGenerator;

    @Setup
    public void setup() {
        snowflakeGenerator = new EventIdGenerationBenchmark.SnowflakeIdGenerator(1, 1);
    }

    /**
     * UUID String 객체 생성 및 메모리 할당
     *
     * 예상 메모리 사용량:
     * - String 객체: 24 bytes (객체 헤더 12 + char[] 참조 4 + 기타 8)
     * - char[] 배열: 36 chars * 2 bytes = 72 bytes
     * - 총: ~96 bytes per UUID
     */
    @Benchmark
    public void allocateUUIDString(Blackhole blackhole) {
        String eventId = UUID.randomUUID().toString();
        blackhole.consume(eventId);
    }

    /**
     * Snowflake Long (primitive) - 스택 할당
     *
     * 예상 메모리 사용량:
     * - long primitive: 8 bytes (스택에 할당, GC 대상 아님)
     */
    @Benchmark
    public void allocateSnowflakeLong(Blackhole blackhole) {
        long eventId = snowflakeGenerator.nextId();
        blackhole.consume(eventId);
    }

    /**
     * Snowflake Long을 String으로 변환 (Base62)
     *
     * 예상 메모리 사용량:
     * - String 객체: 24 bytes
     * - char[] 배열: ~11 chars * 2 bytes = 22 bytes
     * - 총: ~46 bytes per ID
     */
    @Benchmark
    public void allocateSnowflakeString(Blackhole blackhole) {
        long id = snowflakeGenerator.nextId();
        String eventId = EventIdGenerationBenchmark.Base62.encode(id);
        blackhole.consume(eventId);
    }

    /**
     * UUID + UrlClickedEvent 객체 생성 (현재 방식)
     * 실제 사용 시나리오 시뮬레이션
     */
    @Benchmark
    public void allocateEventWithUUID(Blackhole blackhole) {
        String eventId = UUID.randomUUID().toString();
        MockEvent event = new MockEvent(
            eventId,
            "testCode",
            "https://example.com/very/long/url/path/that/might/be/used/in/real/world"
        );
        blackhole.consume(event);
    }

    /**
     * Snowflake Long + UrlClickedEvent 객체 생성 (제안 방식)
     * 실제 사용 시나리오 시뮬레이션
     */
    @Benchmark
    public void allocateEventWithSnowflakeLong(Blackhole blackhole) {
        long eventId = snowflakeGenerator.nextId();
        MockEventLong event = new MockEventLong(
            eventId,
            "testCode",
            "https://example.com/very/long/url/path/that/might/be/used/in/real/world"
        );
        blackhole.consume(event);
    }

    /**
     * Mock Event 클래스 (String eventId)
     */
    static class MockEvent {
        private final String eventId;
        private final String shortCode;
        private final String originalUrl;

        public MockEvent(String eventId, String shortCode, String originalUrl) {
            this.eventId = eventId;
            this.shortCode = shortCode;
            this.originalUrl = originalUrl;
        }

        public String getEventId() {
            return eventId;
        }

        public String getShortCode() {
            return shortCode;
        }

        public String getOriginalUrl() {
            return originalUrl;
        }
    }

    /**
     * Mock Event 클래스 (long eventId)
     */
    static class MockEventLong {
        private final long eventId;
        private final String shortCode;
        private final String originalUrl;

        public MockEventLong(long eventId, String shortCode, String originalUrl) {
            this.eventId = eventId;
            this.shortCode = shortCode;
            this.originalUrl = originalUrl;
        }

        public long getEventId() {
            return eventId;
        }

        public String getShortCode() {
            return shortCode;
        }

        public String getOriginalUrl() {
            return originalUrl;
        }
    }
}
