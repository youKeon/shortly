package com.io.shortly.test.unit.id.mock;

import com.io.shortly.shared.id.impl.snowflake.NodeIdManager;

/**
 * NodeIdManager의 테스트용 Fake 구현체
 * Redis 의존성 없이 고정된 workerId, datacenterId 반환
 */
public class FakeNodeIdManager {

    private final long workerId;
    private final long datacenterId;

    public FakeNodeIdManager() {
        this(1L, 1L);
    }

    public FakeNodeIdManager(long workerId, long datacenterId) {
        if (workerId < 0 || workerId > 31) {
            throw new IllegalArgumentException("Worker ID must be between 0 and 31");
        }
        if (datacenterId < 0 || datacenterId > 31) {
            throw new IllegalArgumentException("Datacenter ID must be between 0 and 31");
        }
        this.workerId = workerId;
        this.datacenterId = datacenterId;
    }

    public long getWorkerId() {
        return workerId;
    }

    public long getDatacenterId() {
        return datacenterId;
    }
}
