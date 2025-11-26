package com.io.shortly.shared.api.support;

import com.io.shortly.shared.api.support.error.BusinessException;
import com.io.shortly.shared.api.support.error.CommonErrorCode;
import com.io.shortly.shared.api.support.error.ErrorCode;
import com.io.shortly.shared.api.support.error.ErrorResponse;
import com.io.shortly.shared.api.support.error.RetryPolicy;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;

public abstract class BaseGlobalExceptionHandler {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    @org.springframework.beans.factory.annotation.Value("${spring.application.name:unknown-service}")
    private String serviceName;

    protected String getServiceName() {
        return serviceName;
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException ex, HttpServletRequest request) {
        return createErrorResponse(ex, ex.getErrorCode(), request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException ex,
            HttpServletRequest request) {
        return createErrorResponse(ex, CommonErrorCode.INVALID_INPUT, request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception ex, HttpServletRequest request) {
        return createErrorResponse(ex, CommonErrorCode.INTERNAL_SERVER_ERROR, request);
    }

    protected ResponseEntity<ErrorResponse> createErrorResponse(Exception ex, ErrorCode errorCode,
            HttpServletRequest request) {
        if (errorCode.getHttpStatus().is5xxServerError()) {
            log.error("Internal Server Error", ex);
        } else {
            log.warn("Client Error: {}", ex.getMessage());
        }

        ErrorResponse response = ErrorResponse.of(
                errorCode,
                ex.getMessage(),
                request.getRequestURI(),
                getServiceName());

        ResponseEntity.BodyBuilder builder = ResponseEntity.status(errorCode.getHttpStatus());
        addRetryHeaderIfNeeded(builder, errorCode);

        return builder.body(response);
    }

    private void addRetryHeaderIfNeeded(ResponseEntity.BodyBuilder builder, ErrorCode errorCode) {
        if (errorCode == CommonErrorCode.SERVICE_UNAVAILABLE
                || errorCode == CommonErrorCode.CIRCUIT_BREAKER_OPEN) {
            builder.header("Retry-After", RetryPolicy.MEDIUM.getHeaderValue());
        } else if (errorCode == CommonErrorCode.TIMEOUT) {
            builder.header("Retry-After", RetryPolicy.SHORT.getHeaderValue());
        } else if (errorCode == CommonErrorCode.MESSAGE_PROCESSING_FAILED) {
            builder.header("Retry-After", RetryPolicy.LONG.getHeaderValue());
        }
    }
}
