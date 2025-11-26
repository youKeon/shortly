package com.io.shortly.shared.api.support.error;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum RetryPolicy {

    SHORT("60"),

    MEDIUM("120"),

    LONG("300"),

    VERY_LONG("900"),

    NONE(null);

    private final String retryAfterSeconds;

    public String getHeaderValue() {
        return retryAfterSeconds;
    }
}
