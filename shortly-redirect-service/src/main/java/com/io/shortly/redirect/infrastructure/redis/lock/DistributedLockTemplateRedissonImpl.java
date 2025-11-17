package com.io.shortly.redirect.infrastructure.redis.lock;

import com.io.shortly.redirect.domain.DistributedLockTemplate;
import com.io.shortly.redirect.domain.LockAcquisitionFailedException;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DistributedLockTemplateRedissonImpl implements DistributedLockTemplate {

    private final RedissonClient redissonClient;

    private static final long LOCK_WAIT_TIME = 5L;
    private static final long LOCK_LEASE_TIME = 10L;
    private static final TimeUnit LOCK_TIME_UNIT = TimeUnit.SECONDS;

    @Override
    public <T> T executeWithLock(String lockKey, Supplier<T> task) {
        RLock lock = redissonClient.getLock(lockKey);

        try {
            boolean acquired = lock.tryLock(LOCK_WAIT_TIME, LOCK_LEASE_TIME, LOCK_TIME_UNIT);

            if (!acquired) {
                log.warn("[DistributedLock] 락 획득 실패 (timeout): lockKey={}", lockKey);
                throw new LockAcquisitionFailedException(lockKey);
            }

            log.info("[DistributedLock] 락 획득 성공: lockKey={}", lockKey);

            // 락 보호 하에 작업 실행
            return task.get();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("[DistributedLock] 락 획득 중 인터럽트: lockKey={}", lockKey, e);
            throw new LockAcquisitionFailedException(lockKey, e);

        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.debug("[DistributedLock] 락 해제: lockKey={}", lockKey);
            }
        }
    }
}
