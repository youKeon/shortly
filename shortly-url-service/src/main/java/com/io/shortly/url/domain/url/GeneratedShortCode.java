package com.io.shortly.url.domain.url;

/**
 * Snowflake ID와 Short Code를 함께 담는 Value Object
 *
 * @param snowflakeId Snowflake ID (Event ID로 사용)
 * @param shortCode   Base62 인코딩된 Short Code
 */
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
