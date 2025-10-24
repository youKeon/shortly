package com.io.shortly.url.api.support;

import com.io.shortly.shared.exception.BaseExceptionHandler;
import com.io.shortly.shared.exception.ErrorResponse;
import com.io.shortly.shared.exception.RetryPolicy;
import com.io.shortly.url.domain.ShortCodeGenerationFailedException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler extends BaseExceptionHandler {

    @Value("${spring.application.name:url-service}")
    private String serviceName;

    @ExceptionHandler(ShortCodeGenerationFailedException.class)
    public ResponseEntity<ErrorResponse> handleShortCodeGenerationFailedException(
            ShortCodeGenerationFailedException ex,
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
                .header("Retry-After", RetryPolicy.SHORT.getHeaderValue())
                .body(errorResponse);
    }
}
