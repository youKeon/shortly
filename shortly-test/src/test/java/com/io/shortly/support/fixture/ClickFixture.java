package com.io.shortly.support.fixture;

import java.time.LocalDateTime;

/**
 * Test fixture for UrlClick domain objects
 */
public class ClickFixture {

    public static final String DEFAULT_SHORT_CODE = "click99";
    public static final String DEFAULT_ORIGINAL_URL = "https://example.com/clicked";
    public static final LocalDateTime DEFAULT_CLICKED_AT = LocalDateTime.of(2025, 1, 17, 14, 30);

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Long id = 1L;
        private String shortCode = DEFAULT_SHORT_CODE;
        private String originalUrl = DEFAULT_ORIGINAL_URL;
        private LocalDateTime clickedAt = DEFAULT_CLICKED_AT;

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

        public Builder clickedAt(LocalDateTime clickedAt) {
            this.clickedAt = clickedAt;
            return this;
        }

        public ClickData build() {
            return new ClickData(id, shortCode, originalUrl, clickedAt);
        }
    }

    public record ClickData(
        Long id,
        String shortCode,
        String originalUrl,
        LocalDateTime clickedAt
    ) {}
}
