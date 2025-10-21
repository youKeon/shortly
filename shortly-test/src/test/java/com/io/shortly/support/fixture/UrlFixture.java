package com.io.shortly.support.fixture;

import java.time.LocalDateTime;

/**
 * Test fixture for ShortUrl domain objects
 */
public class UrlFixture {

    public static final String DEFAULT_SHORT_CODE = "xyz789";
    public static final String DEFAULT_ORIGINAL_URL = "https://example.com/very-long-url";
    public static final LocalDateTime DEFAULT_CREATED_AT = LocalDateTime.of(2025, 1, 17, 12, 0);

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Long id = 1L;
        private String shortCode = DEFAULT_SHORT_CODE;
        private String originalUrl = DEFAULT_ORIGINAL_URL;
        private LocalDateTime createdAt = DEFAULT_CREATED_AT;

        public Builder id(Long id) {
            this.id = id;
            return this;
        }

        public Builder shortCode(String shortCode) {
            this.shortCode = shortCode;
            return this;
        }

        public Builder originalUrl(String originalUrl) {
            this.originalUrl = originalUrl;
            return this;
        }

        public Builder createdAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public UrlData build() {
            return new UrlData(id, shortCode, originalUrl, createdAt);
        }
    }

    public record UrlData(
        Long id,
        String shortCode,
        String originalUrl,
        LocalDateTime createdAt
    ) {}
}
