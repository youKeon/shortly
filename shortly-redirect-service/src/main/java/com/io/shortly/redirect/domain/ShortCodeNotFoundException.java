package com.io.shortly.redirect.domain;

import com.io.shortly.redirect.api.support.RedirectErrorCode;
import com.io.shortly.shared.api.support.error.BusinessException;

public class ShortCodeNotFoundException extends BusinessException {

    private final String shortCode;

    public ShortCodeNotFoundException(String shortCode) {
        super(RedirectErrorCode.SHORT_CODE_NOT_FOUND, String.format("Short code not found: %s", shortCode));
        this.shortCode = shortCode;
    }

    public String getShortCode() {
        return shortCode;
    }
}
