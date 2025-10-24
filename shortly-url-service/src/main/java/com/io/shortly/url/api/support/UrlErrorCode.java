package com.io.shortly.url.api.support;

import com.io.shortly.shared.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum UrlErrorCode implements ErrorCode {

    SHORT_CODE_GENERATION_FAILED("URL-001", "Failed to generate unique short code", HttpStatus.SERVICE_UNAVAILABLE),
    INVALID_URL_FORMAT("URL-002", "Invalid URL format", HttpStatus.BAD_REQUEST),
    INVALID_SHORT_CODE("URL-003", "Invalid short code format", HttpStatus.BAD_REQUEST);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;
}
