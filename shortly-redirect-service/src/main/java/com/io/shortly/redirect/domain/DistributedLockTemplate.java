package com.io.shortly.redirect.domain;

import java.util.function.Supplier;

/**
 * 분산 락 실행 템플릿
 *
 * <p>분산 락을 획득하여 작업을 실행하는 인터페이스입니다.
 * <p>Spring의 {@code TransactionTemplate}과 유사한 패턴을 따릅니다.
 *
 * <p><strong>Note:</strong> 메서드 레벨 제네릭을 사용하므로 {@code @FunctionalInterface}로 만들 수 없습니다.
 *
 * @see java.util.function.Supplier
 */
public interface DistributedLockTemplate {

    /**
     * 분산 락을 획득하여 작업을 실행합니다.
     *
     * @param lockKey 락 키
     * @param task 락 보호 하에 실행할 작업
     * @param <T> 작업 결과 타입
     * @return 작업 실행 결과
     * @throws com.io.shortly.redirect.domain.LockAcquisitionFailedException 락 획득 실패 시
     */
    <T> T executeWithLock(String lockKey, Supplier<T> task);
}
