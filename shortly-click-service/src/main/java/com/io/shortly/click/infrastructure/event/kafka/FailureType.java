package com.io.shortly.click.infrastructure.event.kafka;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 메시지 처리 실패 유형
 */
@Getter
@RequiredArgsConstructor
public enum FailureType {

    /**
     * 일시적 실패 - 재시도 가능
     */
    TRANSIENT("일시적 실패", true),

    /**
     * 영구적 실패 - 재시도 불가
     */
    PERMANENT("영구적 실패", false),

    /**
     * 중복 이벤트 - 스킵
     */
    DUPLICATE("중복 이벤트", false);

    private final String description;
    private final boolean retryable;
}
