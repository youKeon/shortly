package com.io.shortly.shared.exception;

import io.micrometer.tracing.Tracer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Slf4j
public abstract class BaseReactiveExceptionHandler {

    @Value("${spring.application.name:unknown-service}")
    private String serviceName;

    @Autowired(required = false)
    private Tracer tracer;

    /**
     * Extract trace ID
     */
    protected String getTraceId() {
        if (tracer != null && tracer.currentSpan() != null && tracer.currentSpan().context() != null) {
            return tracer.currentSpan().context().traceId();
        }
        return null;
    }

    @ExceptionHandler(BusinessException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleBusinessException(
            BusinessException ex,
            ServerWebExchange exchange
    ) {
        String traceId = getTraceId();
        logError(ex, exchange, traceId);

        ErrorResponse errorResponse = ErrorResponse.of(
                ex.getErrorCode(),
                ex.getMessage(),
                exchange.getRequest().getPath().value(),
                serviceName,
                traceId
        );

        return Mono.just(ResponseEntity
                .status(ex.getErrorCode().getHttpStatus())
                .body(errorResponse));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleIllegalArgumentException(
            IllegalArgumentException ex,
            ServerWebExchange exchange
    ) {
        String traceId = getTraceId();
        logWarn(ex, exchange, traceId);

        ErrorResponse errorResponse = ErrorResponse.of(
                CommonErrorCode.INVALID_INPUT,
                ex.getMessage(),
                exchange.getRequest().getPath().value(),
                serviceName,
                traceId
        );

        return Mono.just(ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(errorResponse));
    }

    @ExceptionHandler(IllegalStateException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleIllegalStateException(
            IllegalStateException ex,
            ServerWebExchange exchange
    ) {
        String traceId = getTraceId();
        logError(ex, exchange, traceId);

        ErrorResponse errorResponse = ErrorResponse.of(
                CommonErrorCode.INTERNAL_SERVER_ERROR,
                ex.getMessage(),
                exchange.getRequest().getPath().value(),
                serviceName,
                traceId
        );

        return Mono.just(ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(errorResponse));
    }

    @ExceptionHandler(MessageProcessingException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleMessageProcessingException(
            MessageProcessingException ex,
            ServerWebExchange exchange
    ) {
        String traceId = getTraceId();
        logError(ex, exchange, traceId);

        ErrorResponse errorResponse = ErrorResponse.of(
                ex.getErrorCode(),
                String.format("Message processing failed: topic=%s, key=%s", ex.getTopic(), ex.getKey()),
                exchange.getRequest().getPath().value(),
                serviceName,
                traceId
        );

        return Mono.just(ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .header("Retry-After", RetryPolicy.LONG.getHeaderValue())
                .body(errorResponse));
    }

    @ExceptionHandler(ServiceTimeoutException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleServiceTimeoutException(
            ServiceTimeoutException ex,
            ServerWebExchange exchange
    ) {
        String traceId = getTraceId();
        logWarn(ex, exchange, traceId);

        ErrorResponse errorResponse = ErrorResponse.of(
                ex.getErrorCode(),
                ex.getMessage(),
                exchange.getRequest().getPath().value(),
                serviceName,
                traceId
        );

        return Mono.just(ResponseEntity
                .status(HttpStatus.GATEWAY_TIMEOUT)
                .header("Retry-After", RetryPolicy.SHORT.getHeaderValue())
                .body(errorResponse));
    }

    @ExceptionHandler(ServiceUnavailableException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleServiceUnavailableException(
            ServiceUnavailableException ex,
            ServerWebExchange exchange
    ) {
        String traceId = getTraceId();
        logError(ex, exchange, traceId);

        ErrorResponse errorResponse = ErrorResponse.of(
                ex.getErrorCode(),
                ex.getMessage(),
                exchange.getRequest().getPath().value(),
                serviceName,
                traceId
        );

        return Mono.just(ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .header("Retry-After", RetryPolicy.MEDIUM.getHeaderValue())
                .body(errorResponse));
    }

    @ExceptionHandler(Exception.class)
    public Mono<ResponseEntity<ErrorResponse>> handleException(
            Exception ex,
            ServerWebExchange exchange
    ) {
        String traceId = getTraceId();
        logError(ex, exchange, traceId);

        ErrorResponse errorResponse = ErrorResponse.of(
                CommonErrorCode.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred",
                exchange.getRequest().getPath().value(),
                serviceName,
                traceId
        );

        return Mono.just(ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(errorResponse));
    }

    /**
     * Log error with standardized format: [API] Operation: key=value
     */
    protected void logError(Exception ex, ServerWebExchange exchange, String traceId) {
        log.error("[API] Request failed: traceId={}, method={}, path={}, exception={}, message={}",
                traceId,
                exchange.getRequest().getMethod(),
                exchange.getRequest().getPath().value(),
                ex.getClass().getSimpleName(),
                ex.getMessage(),
                ex
        );
    }

    /**
     * Log warning with standardized format: [API] Operation: key=value
     */
    protected void logWarn(Exception ex, ServerWebExchange exchange, String traceId) {
        log.warn("[API] Client error: traceId={}, method={}, path={}, exception={}, message={}",
                traceId,
                exchange.getRequest().getMethod(),
                exchange.getRequest().getPath().value(),
                ex.getClass().getSimpleName(),
                ex.getMessage()
        );
    }
}
