package com.io.shortly.url.domain;

public record GeneratedShortCode(
    long snowflakeId,
    String shortCode
) {
    public GeneratedShortCode {
        if (snowflakeId <= 0) {
            throw new IllegalArgumentException("Snowflake ID must be positive");
        }
        if (shortCode == null || shortCode.isBlank()) {
            throw new IllegalArgumentException("Short code must not be blank");
        }
    }

    public static GeneratedShortCode of(long snowflakeId, String shortCode) {
        return new GeneratedShortCode(snowflakeId, shortCode);
    }
}
