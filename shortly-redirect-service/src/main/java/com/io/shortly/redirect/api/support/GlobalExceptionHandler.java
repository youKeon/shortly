package com.io.shortly.redirect.api.support;

import com.io.shortly.redirect.domain.ShortCodeNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ShortCodeNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleShortCodeNotFound(
        ShortCodeNotFoundException ex,
        HttpServletRequest request
    ) {
        log.warn("[Exception] 단축 코드를 찾을 수 없음: {}", ex.getShortCode());

        ErrorResponse response = ErrorResponse.of(
            RedirectErrorCode.SHORT_CODE_NOT_FOUND,
            request.getRequestURI()
        );

        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(response);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(
        ConstraintViolationException ex,
        HttpServletRequest request
    ) {
        log.warn("[Exception] 유효성 검증 실패: {}", ex.getMessage());

        ErrorResponse response = ErrorResponse.of(
            RedirectErrorCode.INVALID_SHORT_CODE.getCode(),
            ex.getMessage(),
            request.getRequestURI()
        );

        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
        Exception ex,
        HttpServletRequest request
    ) {
        log.error("[Exception] 예상치 못한 오류 발생", ex);

        ErrorResponse response = ErrorResponse.of(
            "INTERNAL_ERROR",
            "An unexpected error occurred",
            request.getRequestURI()
        );

        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(response);
    }
}
