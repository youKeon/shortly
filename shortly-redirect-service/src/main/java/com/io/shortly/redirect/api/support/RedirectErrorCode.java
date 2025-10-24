package com.io.shortly.redirect.api.support;

import com.io.shortly.shared.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * Redirect Service specific error codes
 */
@Getter
@RequiredArgsConstructor
public enum RedirectErrorCode implements ErrorCode {
    // Redirect errors (404)
    SHORT_CODE_NOT_FOUND("REDIRECT-001", "Short code not found", HttpStatus.NOT_FOUND),

    // Cache errors (500)
    CACHE_ERROR("REDIRECT-002", "Cache operation failed", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;
}
