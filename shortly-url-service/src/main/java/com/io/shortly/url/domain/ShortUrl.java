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
        this.id = id;
        this.shortCode = shortCode;
        this.originalUrl = originalUrl;
        this.createdAt = createdAt;
    }

    public static ShortUrl create(String shortCode, String originalUrl) {
        return new ShortUrl(null, shortCode, originalUrl, LocalDateTime.now());
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
