package com.io.shortly.click.domain;

import com.io.shortly.shared.exception.BusinessException;
import com.io.shortly.click.api.support.ClickErrorCode;

public class InvalidShortCodeException extends BusinessException {

    public InvalidShortCodeException(String shortCode) {
        super(
            ClickErrorCode.INVALID_SHORT_CODE,
            String.format("Invalid short code: %s", shortCode)
        );
    }
}
