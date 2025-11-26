package com.io.shortly.redirect.api.support;

import com.io.shortly.shared.api.support.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum RedirectErrorCode implements ErrorCode {
    SHORT_CODE_NOT_FOUND("RD001", "Short code not found", HttpStatus.NOT_FOUND),
    INVALID_SHORT_CODE("RD002", "Invalid short code format", HttpStatus.BAD_REQUEST),
    CACHE_ERROR("RD003", "Cache operation failed", HttpStatus.INTERNAL_SERVER_ERROR),
    EVENT_PUBLISH_ERROR("RD004", "Event publish failed", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;
}
