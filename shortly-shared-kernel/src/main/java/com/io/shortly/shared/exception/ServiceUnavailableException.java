package com.io.shortly.shared.exception;

import lombok.Getter;

@Getter
public class ServiceUnavailableException extends BusinessException {

    private final String serviceName;
    private final String reason;

    public ServiceUnavailableException(String serviceName, String reason) {
        super(
            CommonErrorCode.SERVICE_UNAVAILABLE,
            String.format("Service unavailable: service=%s, reason=%s", serviceName, reason)
        );
        this.serviceName = serviceName;
        this.reason = reason;
    }

    public ServiceUnavailableException(String serviceName, String reason, Throwable cause) {
        super(
            CommonErrorCode.SERVICE_UNAVAILABLE,
            String.format("Service unavailable: service=%s, reason=%s", serviceName, reason),
            cause
        );
        this.serviceName = serviceName;
        this.reason = reason;
    }

    /**
     * Create exception for circuit breaker open scenario
     */
    public static ServiceUnavailableException circuitBreakerOpen(String serviceName) {
        return new ServiceUnavailableException(serviceName, "Circuit breaker is open");
    }
}
