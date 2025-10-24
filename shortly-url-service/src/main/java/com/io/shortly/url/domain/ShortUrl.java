package com.io.shortly.url.domain;

import java.time.LocalDateTime;
import java.util.Objects;

public class ShortUrl {

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
        validateShortCode(shortCode);
        validateOriginalUrl(originalUrl);

        this.id = id;
        this.shortCode = shortCode;
        this.originalUrl = originalUrl;
        this.createdAt = createdAt != null ? createdAt : LocalDateTime.now();
    }

    public static ShortUrl create(String shortCode, String originalUrl) {
        return new ShortUrl(null, shortCode, originalUrl, LocalDateTime.now());
    }

    private static void validateShortCode(String shortCode) {
        if (shortCode == null || shortCode.isBlank()) {
            throw new IllegalArgumentException("Short code must not be blank");
        }
        if (shortCode.length() < 6 || shortCode.length() > 10) {
            throw new IllegalArgumentException("Short code must be 6-10 characters");
        }
        if (!shortCode.matches("^[a-zA-Z0-9]+$")) {
            throw new IllegalArgumentException("Short code must be alphanumeric");
        }
    }

    private static void validateOriginalUrl(String originalUrl) {
        if (originalUrl == null || originalUrl.isBlank()) {
            throw new IllegalArgumentException("Original URL must not be blank");
        }
        if (originalUrl.length() > 2048) {
            throw new IllegalArgumentException("URL must not exceed 2048 characters");
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
        return Objects.equals(shortCode, shortUrl.shortCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(shortCode);
    }
}
