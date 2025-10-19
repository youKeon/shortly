package com.io.shortly.url.domain;

import com.io.shortly.shared.exception.BusinessException;
import com.io.shortly.url.api.support.UrlErrorCode;

public class ShortCodeGenerationFailedException extends BusinessException {

    public ShortCodeGenerationFailedException(int maxAttempts) {
        super(
            UrlErrorCode.SHORT_CODE_GENERATION_FAILED,
            String.format(
                "Failed to generate unique short code after %d attempts. Please try again later.",
                maxAttempts
            )
        );
    }
}
