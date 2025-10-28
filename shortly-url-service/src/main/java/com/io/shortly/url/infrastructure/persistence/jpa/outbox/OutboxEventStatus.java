package com.io.shortly.url.infrastructure.persistence.jpa.outbox;

public enum OutboxEventStatus {
    PENDING,    // 발행 대기
    PUBLISHED   // 발행 완료
}
