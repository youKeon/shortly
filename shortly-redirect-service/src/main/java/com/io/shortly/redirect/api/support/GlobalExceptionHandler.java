package com.io.shortly.redirect.api.support;

import com.io.shortly.shared.api.support.BaseGlobalExceptionHandler;
import com.io.shortly.shared.api.support.error.CommonErrorCode;
import com.io.shortly.shared.api.support.error.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler extends BaseGlobalExceptionHandler {

    @ExceptionHandler(com.io.shortly.redirect.domain.ShortCodeNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleShortCodeNotFound(
            com.io.shortly.redirect.domain.ShortCodeNotFoundException ex,
            HttpServletRequest request) {
        return createErrorResponse(ex, ex.getErrorCode(), request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex,
            HttpServletRequest request) {
        return createErrorResponse(ex, CommonErrorCode.VALIDATION_FAILED, request);
    }
}
