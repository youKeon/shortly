package com.io.shortly.click.api.support;

import com.io.shortly.shared.exception.BaseExceptionHandler;
import com.io.shortly.shared.exception.ErrorResponse;
import com.io.shortly.click.domain.InvalidShortCodeException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler extends BaseExceptionHandler {

    @Value("${spring.application.name:click-service}")
    private String serviceName;

    @ExceptionHandler(InvalidShortCodeException.class)
    public ResponseEntity<ErrorResponse> handleInvalidShortCodeException(
            InvalidShortCodeException ex,
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
                .status(ex.getErrorCode().getHttpStatus())
                .body(errorResponse);
    }
}
