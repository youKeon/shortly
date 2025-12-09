package com.io.shortly.url.domain;

import java.time.LocalDateTime;
import java.util.Objects;

public class ShortUrl {

    private static final int MIN_SHORT_CODE_LENGTH = 6;
    private static final int MAX_SHORT_CODE_LENGTH = 10;
    private static final String SHORT_CODE_PATTERN = "^[a-zA-Z0-9]+$";
    private static final int MAX_ORIGINAL_URL_LENGTH = 2048;

    private final Long id;
    private final String shortCode;
    private final String originalUrl;
    private final LocalDateTime createdAt;

    private ShortUrl(
            final Long id,
            final String shortCode,
            final String originalUrl,
            final LocalDateTime createdAt
    ) {
        if (createdAt == null) {
            throw new IllegalArgumentException("createdAt must not be null");
        }
        validateShortCode(shortCode);
        validateOriginalUrl(originalUrl);

        this.id = id;
        this.shortCode = shortCode;
        this.originalUrl = originalUrl;
        this.createdAt = createdAt;
    }

    public static ShortUrl create(String shortCode, String originalUrl) {
        return new ShortUrl(null, shortCode, originalUrl, LocalDateTime.now());
    }

    private static void validateShortCode(String shortCode) {
        if (shortCode == null || shortCode.isBlank()) {
            throw new IllegalArgumentException("Short code must not be blank");
        }
        if (shortCode.length() < MIN_SHORT_CODE_LENGTH || shortCode.length() > MAX_SHORT_CODE_LENGTH) {
            throw new IllegalArgumentException("Short code must be " + MIN_SHORT_CODE_LENGTH + "-" + MAX_SHORT_CODE_LENGTH + " characters");
        }
        if (!shortCode.matches(SHORT_CODE_PATTERN)) {
            throw new IllegalArgumentException("Short code must be alphanumeric");
        }
    }

    private static void validateOriginalUrl(String originalUrl) {
        if (originalUrl == null || originalUrl.isBlank()) {
            throw new IllegalArgumentException("Original URL must not be blank");
        }
        if (originalUrl.length() > MAX_ORIGINAL_URL_LENGTH) {
            throw new IllegalArgumentException("URL must not exceed " + MAX_ORIGINAL_URL_LENGTH + " characters");
        }
    }

    public static ShortUrl restore(Long id, String shortCode, String originalUrl, LocalDateTime createdAt) {
        return new ShortUrl(id, shortCode, originalUrl, createdAt);
    }

    public Long getId() {
        return id;
    }

    public String getShortCode() {
        return shortCode;
    }

    public String getOriginalUrl() {
        return originalUrl;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ShortUrl shortUrl = (ShortUrl) o;
        return Objects.equals(shortCode, shortUrl.shortCode) &&
               Objects.equals(originalUrl, shortUrl.originalUrl);
    }

    @Override
    public int hashCode() {
        return Objects.hash(shortCode, originalUrl);
    }
}
