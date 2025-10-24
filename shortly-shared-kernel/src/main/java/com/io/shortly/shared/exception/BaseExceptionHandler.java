package com.io.shortly.shared.exception;

import io.micrometer.tracing.Tracer;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;

@Slf4j
public abstract class BaseExceptionHandler {

    @Value("${spring.application.name:unknown-service}")
    private String serviceName;

    @Autowired(required = false)
    private Tracer tracer;

    /**
     * Extract trace ID
     */
    protected String getTraceId() {
        if (tracer != null && tracer.currentSpan() != null) {
            tracer.currentSpan().context();
            return tracer.currentSpan().context().traceId();
        }
        return null;
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(
            BusinessException ex,
            HttpServletRequest request
    ) {
        String traceId = getTraceId();
        logError(ex, request, traceId);

        ErrorResponse errorResponse = ErrorResponse.of(
                ex.getErrorCode(),
                ex.getMessage(),
                request.getRequestURI(),
                serviceName,
                traceId
        );

        return ResponseEntity
                .status(ex.getErrorCode().getHttpStatus())
                .body(errorResponse);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(
            IllegalArgumentException ex,
            HttpServletRequest request
    ) {
        String traceId = getTraceId();
        logWarn(ex, request, traceId);

        ErrorResponse errorResponse = ErrorResponse.of(
                CommonErrorCode.INVALID_INPUT,
                ex.getMessage(),
                request.getRequestURI(),
                serviceName,
                traceId
        );

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(errorResponse);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalStateException(
            IllegalStateException ex,
            HttpServletRequest request
    ) {
        String traceId = getTraceId();
        logError(ex, request, traceId);

        ErrorResponse errorResponse = ErrorResponse.of(
                CommonErrorCode.INTERNAL_SERVER_ERROR,
                ex.getMessage(),
                request.getRequestURI(),
                serviceName,
                traceId
        );

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(errorResponse);
    }

    @ExceptionHandler(MessageProcessingException.class)
    public ResponseEntity<ErrorResponse> handleMessageProcessingException(
            MessageProcessingException ex,
            HttpServletRequest request
    ) {
        String traceId = getTraceId();
        logError(ex, request, traceId);

        ErrorResponse errorResponse = ErrorResponse.of(
                ex.getErrorCode(),
                String.format("Message processing failed: topic=%s, key=%s", ex.getTopic(), ex.getKey()),
                request.getRequestURI(),
                serviceName,
                traceId
        );

        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .header("Retry-After", RetryPolicy.LONG.getHeaderValue())
                .body(errorResponse);
    }

    @ExceptionHandler(ServiceTimeoutException.class)
    public ResponseEntity<ErrorResponse> handleServiceTimeoutException(
            ServiceTimeoutException ex,
            HttpServletRequest request
    ) {
        String traceId = getTraceId();
        logWarn(ex, request, traceId);

        ErrorResponse errorResponse = ErrorResponse.of(
                ex.getErrorCode(),
                ex.getMessage(),
                request.getRequestURI(),
                serviceName,
                traceId
        );

        return ResponseEntity
                .status(HttpStatus.GATEWAY_TIMEOUT)
                .header("Retry-After", RetryPolicy.SHORT.getHeaderValue())
                .body(errorResponse);
    }

    @ExceptionHandler(ServiceUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleServiceUnavailableException(
            ServiceUnavailableException ex,
            HttpServletRequest request
    ) {
        String traceId = getTraceId();
        logError(ex, request, traceId);

        ErrorResponse errorResponse = ErrorResponse.of(
                ex.getErrorCode(),
                ex.getMessage(),
                request.getRequestURI(),
                serviceName,
                traceId
        );

        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .header("Retry-After", RetryPolicy.MEDIUM.getHeaderValue())
                .body(errorResponse);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(
            Exception ex,
            HttpServletRequest request
    ) {
        String traceId = getTraceId();
        logError(ex, request, traceId);

        ErrorResponse errorResponse = ErrorResponse.of(
                CommonErrorCode.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred",
                request.getRequestURI(),
                serviceName,
                traceId
        );

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(errorResponse);
    }

    /**
     * Log error with standardized format: [API] Operation: key=value
     */
    protected void logError(Exception ex, HttpServletRequest request, String traceId) {
        log.error("[API] Request failed: traceId={}, method={}, path={}, exception={}, message={}",
                traceId,
                request.getMethod(),
                request.getRequestURI(),
                ex.getClass().getSimpleName(),
                ex.getMessage(),
                ex
        );
    }

    /**
     * Log warning with standardized format: [API] Operation: key=value
     */
    protected void logWarn(Exception ex, HttpServletRequest request, String traceId) {
        log.warn("[API] Client error: traceId={}, method={}, path={}, exception={}, message={}",
                traceId,
                request.getMethod(),
                request.getRequestURI(),
                ex.getClass().getSimpleName(),
                ex.getMessage()
        );
    }
}
