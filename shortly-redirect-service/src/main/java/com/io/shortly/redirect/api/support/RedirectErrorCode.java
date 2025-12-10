package com.io.shortly.redirect.api.support;

import com.io.shortly.shared.api.support.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum RedirectErrorCode implements ErrorCode {
    SHORT_CODE_NOT_FOUND("RD001", "Short code not found", HttpStatus.NOT_FOUND);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;
}
