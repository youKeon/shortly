package com.io.shortly.url.api.support;

import com.io.shortly.shared.exception.BaseExceptionHandler;
import com.io.shortly.shared.exception.ErrorResponse;
import com.io.shortly.url.domain.ShortCodeGenerationFailedException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler extends BaseExceptionHandler {

    @ExceptionHandler(ShortCodeGenerationFailedException.class)
    public ResponseEntity<ErrorResponse> handleShortCodeGenerationFailedException(
            ShortCodeGenerationFailedException ex,
            HttpServletRequest request
    ) {
        ErrorResponse errorResponse = ErrorResponse.of(
                ex.getErrorCode(),
                ex.getMessage(),
                request.getRequestURI()
        );

        return ResponseEntity
                .status(ex.getErrorCode().getHttpStatus())
                .header("Retry-After", "60") // Suggest retry after 60 seconds
                .body(errorResponse);
    }
}
