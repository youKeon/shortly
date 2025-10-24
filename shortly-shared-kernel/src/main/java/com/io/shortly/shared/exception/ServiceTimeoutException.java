package com.io.shortly.shared.exception;

import lombok.Getter;

import java.time.Duration;

@Getter
public class ServiceTimeoutException extends BusinessException {

    private final String operation;
    private final Duration timeout;

    public ServiceTimeoutException(String operation, Duration timeout) {
        super(
            CommonErrorCode.TIMEOUT,
            String.format("Operation timed out: operation=%s, timeout=%s", operation, timeout)
        );
        this.operation = operation;
        this.timeout = timeout;
    }

    public ServiceTimeoutException(String operation, Duration timeout, Throwable cause) {
        super(
            CommonErrorCode.TIMEOUT,
            String.format("Operation timed out: operation=%s, timeout=%s", operation, timeout),
            cause
        );
        this.operation = operation;
        this.timeout = timeout;
    }
}
