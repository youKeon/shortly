package com.io.shortly.url.infrastructure.generator;

import com.io.shortly.url.domain.url.ShortUrlGenerator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
public class ShortUrlGeneratorSnowflakeImpl implements ShortUrlGenerator {

    private static final long CUSTOM_EPOCH = 1704067200000L; // 2024-01-01T00:00:00Z

    private static final long WORKER_ID_BITS = 5L;
    private static final long DATACENTER_ID_BITS = 5L;
    private static final long SEQUENCE_BITS = 12L;

    private static final long MAX_WORKER_ID = ~(-1L << WORKER_ID_BITS);
    private static final long MAX_DATACENTER_ID = ~(-1L << DATACENTER_ID_BITS);
    private static final long SEQUENCE_MASK = ~(-1L << SEQUENCE_BITS);

    private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;
    private static final long DATACENTER_ID_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;
    private static final long TIMESTAMP_LEFT_SHIFT =
        SEQUENCE_BITS + WORKER_ID_BITS + DATACENTER_ID_BITS;

    private static final char[] BASE62 =
        "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray();
    private static final int MIN_SHORT_CODE_LENGTH = 6;

    private final long workerId;
    private final long datacenterId;

    private long sequence = 0L;
    private long lastTimestamp = -1L;

    public ShortUrlGeneratorSnowflakeImpl(
        @Value("${shortly.snowflake.worker-id:0}") long workerId,
        @Value("${shortly.snowflake.datacenter-id:0}") long datacenterId
    ) {
        if (workerId < 0 || workerId > MAX_WORKER_ID) {
            throw new IllegalArgumentException("workerId must be between 0 and " + MAX_WORKER_ID);
        }
        if (datacenterId < 0 || datacenterId > MAX_DATACENTER_ID) {
            throw new IllegalArgumentException(
                "datacenterId must be between 0 and " + MAX_DATACENTER_ID
            );
        }
        this.workerId = workerId;
        this.datacenterId = datacenterId;
    }

    @Override
    public synchronized String generate(String seed) {
        long timestamp = currentTimeMillis();

        if (timestamp < lastTimestamp) {
            throw new IllegalStateException(
                "Clock moved backwards. Refusing to generate id for " + (lastTimestamp - timestamp) + "ms"
            );
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

        long id =
            ((timestamp - CUSTOM_EPOCH) << TIMESTAMP_LEFT_SHIFT)
                | (datacenterId << DATACENTER_ID_SHIFT)
                | (workerId << WORKER_ID_SHIFT)
                | sequence;

        return encodeBase62(id);
    }

    private long currentTimeMillis() {
        return System.currentTimeMillis();
    }

    private long waitNextMillis(long lastTimestamp) {
        long timestamp = currentTimeMillis();
        while (timestamp <= lastTimestamp) {
            timestamp = currentTimeMillis();
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
