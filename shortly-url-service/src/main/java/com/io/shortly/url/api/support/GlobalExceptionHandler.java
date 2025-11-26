package com.io.shortly.url.api.support;

import com.io.shortly.shared.api.support.BaseGlobalExceptionHandler;
import com.io.shortly.shared.api.support.error.ErrorResponse;
import com.io.shortly.url.domain.url.ShortCodeGenerationFailedException;
import com.io.shortly.url.domain.url.ShortCodeNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler extends BaseGlobalExceptionHandler {

    @ExceptionHandler(ShortCodeGenerationFailedException.class)
    public org.springframework.http.ResponseEntity<ErrorResponse> handleShortCodeGenerationFailed(
        ShortCodeGenerationFailedException ex, HttpServletRequest request
    ) {
        return createErrorResponse(ex, ex.getErrorCode(), request);
    }

    @ExceptionHandler(ShortCodeNotFoundException.class)
    public org.springframework.http.ResponseEntity<ErrorResponse> handleShortCodeNotFound(
        ShortCodeNotFoundException ex, HttpServletRequest request
    ) {
        return createErrorResponse(ex, ex.getErrorCode(), request);
    }
}
