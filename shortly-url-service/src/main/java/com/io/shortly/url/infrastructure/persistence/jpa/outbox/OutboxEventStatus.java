package com.io.shortly.url.infrastructure.persistence.jpa.outbox;

public enum OutboxEventStatus {
    PENDING,      // 발행 대기
    PROCESSING,   // 발행 처리 중
    PUBLISHED     // 발행 완료
}
