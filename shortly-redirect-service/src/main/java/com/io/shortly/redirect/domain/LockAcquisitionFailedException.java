package com.io.shortly.redirect.domain;

/**
 * 분산 락 획득 실패 예외
 *
 * <p>분산 락을 획득하지 못했거나 락 획득 중 인터럽트가 발생한 경우 발생합니다.
 */
public class LockAcquisitionFailedException extends RuntimeException {

    public LockAcquisitionFailedException(String lockKey) {
        super("Failed to acquire lock: " + lockKey);
    }

    public LockAcquisitionFailedException(String lockKey, Throwable cause) {
        super("Failed to acquire lock: " + lockKey, cause);
    }
}
