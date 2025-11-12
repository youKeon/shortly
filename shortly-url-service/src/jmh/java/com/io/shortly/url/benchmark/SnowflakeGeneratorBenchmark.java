package com.io.shortly.url.benchmark;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 3)
@Fork(1)
public class SnowflakeGeneratorBenchmark {

    private SynchronizedSnowflakeGenerator synchronizedGenerator;
    private CASSnowflakeGenerator casGenerator;

    @Setup
    public void setup() {
        synchronizedGenerator = new SynchronizedSnowflakeGenerator(0, 0);
        casGenerator = new CASSnowflakeGenerator(0, 0);
    }

    @Benchmark
    @Threads(1)
    public void synchronizedGenerator_1Thread(Blackhole bh) {
        bh.consume(synchronizedGenerator.generate());
    }

    @Benchmark
    @Threads(4)
    public void synchronizedGenerator_4Threads(Blackhole bh) {
        bh.consume(synchronizedGenerator.generate());
    }

    @Benchmark
    @Threads(8)
    public void synchronizedGenerator_8Threads(Blackhole bh) {
        bh.consume(synchronizedGenerator.generate());
    }

    @Benchmark
    @Threads(16)
    public void synchronizedGenerator_16Threads(Blackhole bh) {
        bh.consume(synchronizedGenerator.generate());
    }

    @Benchmark
    @Threads(32)
    public void synchronizedGenerator_32Threads(Blackhole bh) {
        bh.consume(synchronizedGenerator.generate());
    }

    @Benchmark
    @Threads(1)
    public void casGenerator_1Thread(Blackhole bh) {
        bh.consume(casGenerator.generate());
    }

    @Benchmark
    @Threads(4)
    public void casGenerator_4Threads(Blackhole bh) {
        bh.consume(casGenerator.generate());
    }

    @Benchmark
    @Threads(8)
    public void casGenerator_8Threads(Blackhole bh) {
        bh.consume(casGenerator.generate());
    }

    @Benchmark
    @Threads(16)
    public void casGenerator_16Threads(Blackhole bh) {
        bh.consume(casGenerator.generate());
    }

    @Benchmark
    @Threads(32)
    public void casGenerator_32Threads(Blackhole bh) {
        bh.consume(casGenerator.generate());
    }

    /**
     * Synchronized 방식 (기존 구현)
     */
    public static class SynchronizedSnowflakeGenerator {
        private static final long CUSTOM_EPOCH = 1704067200000L;
        private static final long SEQUENCE_BITS = 12L;
        private static final long WORKER_ID_BITS = 5L;
        private static final long DATACENTER_ID_BITS = 5L;
        private static final long SEQUENCE_MASK = ~(-1L << SEQUENCE_BITS);
        private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;
        private static final long DATACENTER_ID_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;
        private static final long TIMESTAMP_LEFT_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS + DATACENTER_ID_BITS;

        private static final char[] BASE62 = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray();
        private static final int MIN_SHORT_CODE_LENGTH = 6;

        private final long workerId;
        private final long datacenterId;
        private long sequence = 0L;
        private long lastTimestamp = -1L;

        public SynchronizedSnowflakeGenerator(long workerId, long datacenterId) {
            this.workerId = workerId;
            this.datacenterId = datacenterId;
        }

        public synchronized String generate() {
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

            long id = ((timestamp - CUSTOM_EPOCH) << TIMESTAMP_LEFT_SHIFT)
                | (datacenterId << DATACENTER_ID_SHIFT)
                | (workerId << WORKER_ID_SHIFT)
                | sequence;

            return encodeBase62(id);
        }

        private long waitNextMillis(long lastTimestamp) {
            long timestamp = System.currentTimeMillis();
            while (timestamp <= lastTimestamp) {
                timestamp = System.currentTimeMillis();
            }
            return timestamp;
        }

        private String encodeBase62(long value) {
            if (value == 0) {
                return "0".repeat(MIN_SHORT_CODE_LENGTH);
            }

            StringBuilder builder = new StringBuilder();
            long current = value;

            while (current > 0) {
                int index = (int) (current % BASE62.length);
                builder.append(BASE62[index]);
                current /= BASE62.length;
            }

            while (builder.length() < MIN_SHORT_CODE_LENGTH) {
                builder.append('0');
            }

            return builder.reverse().toString();
        }
    }

    /**
     * AtomicReference + CAS 방식
     */
    public static class CASSnowflakeGenerator {
        private static final long CUSTOM_EPOCH = 1704067200000L;
        private static final long SEQUENCE_BITS = 12L;
        private static final long WORKER_ID_BITS = 5L;
        private static final long DATACENTER_ID_BITS = 5L;
        private static final long SEQUENCE_MASK = ~(-1L << SEQUENCE_BITS);
        private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;
        private static final long DATACENTER_ID_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;
        private static final long TIMESTAMP_LEFT_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS + DATACENTER_ID_BITS;

        private static final char[] BASE62 = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray();
        private static final int MIN_SHORT_CODE_LENGTH = 6;

        private final long workerId;
        private final long datacenterId;

        private static class SnowflakeState {
            final long timestamp;
            final long sequence;

            SnowflakeState(long timestamp, long sequence) {
                this.timestamp = timestamp;
                this.sequence = sequence;
            }
        }

        private final AtomicReference<SnowflakeState> state = new AtomicReference<>(new SnowflakeState(-1L, 0L));

        public CASSnowflakeGenerator(long workerId, long datacenterId) {
            this.workerId = workerId;
            this.datacenterId = datacenterId;
        }

        public String generate() {
            while (true) {
                SnowflakeState current = state.get();
                long timestamp = System.currentTimeMillis();
                long sequence;

                if (timestamp < current.timestamp) {
                    throw new IllegalStateException("Clock moved backwards");
                }

                if (timestamp == current.timestamp) {
                    sequence = (current.sequence + 1) & SEQUENCE_MASK;
                    if (sequence == 0) {
                        timestamp = waitNextMillis(timestamp);
                        sequence = 0;
                    }
                } else {
                    sequence = 0;
                }

                SnowflakeState next = new SnowflakeState(timestamp, sequence);
                if (state.compareAndSet(current, next)) {
                    long id = ((timestamp - CUSTOM_EPOCH) << TIMESTAMP_LEFT_SHIFT)
                        | (datacenterId << DATACENTER_ID_SHIFT)
                        | (workerId << WORKER_ID_SHIFT)
                        | sequence;
                    return encodeBase62(id);
                }
                // CAS failed, retry
            }
        }

        private long waitNextMillis(long lastTimestamp) {
            long timestamp = System.currentTimeMillis();
            while (timestamp <= lastTimestamp) {
                timestamp = System.currentTimeMillis();
            }
            return timestamp;
        }

        private String encodeBase62(long value) {
            if (value == 0) {
                return "0".repeat(MIN_SHORT_CODE_LENGTH);
            }

            StringBuilder builder = new StringBuilder();
            long current = value;

            while (current > 0) {
                int index = (int) (current % BASE62.length);
                builder.append(BASE62[index]);
                current /= BASE62.length;
            }

            while (builder.length() < MIN_SHORT_CODE_LENGTH) {
                builder.append('0');
            }

            return builder.reverse().toString();
        }
    }
}
