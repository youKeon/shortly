package com.io.shortly.url.domain;

import com.io.shortly.shared.api.support.error.BusinessException;
import com.io.shortly.shared.api.support.error.CommonErrorCode;

public class ShortCodeNotFoundException extends BusinessException {

    private final String shortCode;

    public ShortCodeNotFoundException(String shortCode) {
        super(CommonErrorCode.RESOURCE_NOT_FOUND, String.format("Short code not found: %s", shortCode));
        this.shortCode = shortCode;
    }

    public String getShortCode() {
        return shortCode;
    }
}
