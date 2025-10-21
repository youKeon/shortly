package com.io.shortly.click.api.support;

import com.io.shortly.shared.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ClickErrorCode implements ErrorCode {
    // Click tracking errors (400)
    INVALID_LIMIT("CLICK-001", "Invalid limit parameter", HttpStatus.BAD_REQUEST),

    // Analytics errors (500)
    ANALYTICS_QUERY_FAILED("CLICK-501", "Analytics query failed", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;
}
