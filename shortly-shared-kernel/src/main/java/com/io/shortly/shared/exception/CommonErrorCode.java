package com.io.shortly.shared.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum CommonErrorCode implements ErrorCode {

    // 4xx Client Errors
    INVALID_INPUT("COMMON-400-001", "Invalid input provided", HttpStatus.BAD_REQUEST),
    VALIDATION_FAILED("COMMON-400-002", "Validation failed", HttpStatus.BAD_REQUEST),
    RESOURCE_NOT_FOUND("COMMON-404-001", "Resource not found", HttpStatus.NOT_FOUND),

    // 5xx Server Errors
    INTERNAL_SERVER_ERROR("COMMON-500-001", "Internal server error", HttpStatus.INTERNAL_SERVER_ERROR),
    SERVICE_UNAVAILABLE("COMMON-503-001", "Service temporarily unavailable", HttpStatus.SERVICE_UNAVAILABLE),

    // Services Errors
    MESSAGE_PROCESSING_FAILED("COMMON-500-002", "Message processing failed", HttpStatus.INTERNAL_SERVER_ERROR),
    TIMEOUT("COMMON-504-001", "Request timeout", HttpStatus.GATEWAY_TIMEOUT),
    CIRCUIT_BREAKER_OPEN("COMMON-503-002", "Circuit breaker is open", HttpStatus.SERVICE_UNAVAILABLE);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;
}
