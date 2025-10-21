package com.io.shortly.support.fixture;

import java.time.LocalDateTime;

/**
 * Test fixture for Redirect domain objects
 */
public class RedirectFixture {

    public static final String DEFAULT_SHORT_CODE = "abc123";
    public static final String DEFAULT_TARGET_URL = "https://example.com/test";
    public static final LocalDateTime DEFAULT_CREATED_AT = LocalDateTime.of(2025, 1, 17, 10, 0);

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Long id = 1L;
        private String shortCode = DEFAULT_SHORT_CODE;
        private String targetUrl = DEFAULT_TARGET_URL;
        private LocalDateTime createdAt = DEFAULT_CREATED_AT;

        public Builder id(Long id) {
            this.id = id;
            return this;
        }

        public Builder shortCode(String shortCode) {
            this.shortCode = shortCode;
            return this;
        }

        public Builder targetUrl(String targetUrl) {
            this.targetUrl = targetUrl;
            return this;
        }

        public Builder createdAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public RedirectData build() {
            return new RedirectData(id, shortCode, targetUrl, createdAt);
        }
    }

    public record RedirectData(
        Long id,
        String shortCode,
        String targetUrl,
        LocalDateTime createdAt
    ) {}
}
