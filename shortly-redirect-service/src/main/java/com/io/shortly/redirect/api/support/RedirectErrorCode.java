package com.io.shortly.redirect.api.support;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum RedirectErrorCode {
    SHORT_CODE_NOT_FOUND("RD001", "Short code not found"),
    INVALID_SHORT_CODE("RD002", "Invalid short code format"),
    CACHE_ERROR("RD003", "Cache operation failed"),
    EVENT_PUBLISH_ERROR("RD004", "Event publish failed");

    private final String code;
    private final String message;
}
