package com.io.shortly.click.api.support;

import com.io.shortly.shared.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ClickErrorCode implements ErrorCode {

    INVALID_SHORT_CODE("CLICK-001", "Invalid short code format", HttpStatus.BAD_REQUEST),
    CLICK_NOT_FOUND("CLICK-002", "Click record not found", HttpStatus.NOT_FOUND);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;
}
