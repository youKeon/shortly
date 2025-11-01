package com.io.shortly.redirect.webflux.domain;

import com.io.shortly.shared.exception.BusinessException;
import com.io.shortly.redirect.webflux.api.support.RedirectErrorCode;

public class ShortCodeNotFoundException extends BusinessException {
    public ShortCodeNotFoundException(String shortCode) {
        super(RedirectErrorCode.SHORT_CODE_NOT_FOUND, "Short code not found: " + shortCode);
    }
}
