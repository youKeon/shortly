package com.io.shortly.url.infrastructure.generator;

import com.io.shortly.url.domain.url.GeneratedShortCode;
import com.io.shortly.url.domain.url.ShortUrlGenerator;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
public class ShortUrlGeneratorSnowflakeImpl implements ShortUrlGenerator {

    private static final long CUSTOM_EPOCH = 1704067200000L; // 2024-01-01T00:00:00Z

    private static final long WORKER_ID_BITS = 5L;
    private static final long DATACENTER_ID_BITS = 5L;
    private static final long SEQUENCE_BITS = 12L;

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

    public ShortUrlGeneratorSnowflakeImpl(NodeIdManager nodeIdManager) {
        this.workerId = nodeIdManager.getWorkerId();
        this.datacenterId = nodeIdManager.getDatacenterId();
    }

    @Override
    public synchronized GeneratedShortCode generate(String seed) {
        long timestamp = currentTimeMillis();

        if (timestamp < lastTimestamp) {
            long offset = lastTimestamp - timestamp;
            if (offset <= 10) {
                try {
                    Thread.sleep(offset + 1);
                    timestamp = currentTimeMillis();
                    if (timestamp < lastTimestamp) {
                        throw new IllegalStateException("Clock moved backwards even after waiting.");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while waiting for clock to catch up", e);
                }
            } else {
                throw new IllegalStateException(
                        "Clock moved backwards. Refusing to generate id for " + offset + "ms");
            }
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

        String shortCode = encodeBase62(id);
        return GeneratedShortCode.of(id, shortCode);
    }

    protected long currentTimeMillis() {
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
