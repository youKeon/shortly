package com.io.shortly.url.domain;

import com.io.shortly.shared.api.support.error.BusinessException;
import com.io.shortly.shared.api.support.error.CommonErrorCode;

public class DuplicateShortCodeException extends BusinessException {

    private final String shortCode;

    public DuplicateShortCodeException(String shortCode) {
        super(CommonErrorCode.CONFLICT, String.format("Short code already exists: %s", shortCode));
        this.shortCode = shortCode;
    }

    public String getShortCode() {
        return shortCode;
    }
}
