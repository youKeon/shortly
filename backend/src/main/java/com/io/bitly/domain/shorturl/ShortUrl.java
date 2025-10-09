package com.io.bitly.domain.shorturl;

import java.time.LocalDateTime;

public class ShortUrl {

    private final Long id;
    private final String shortUrl;
    private final String originalUrl;
    private final LocalDateTime createdAt;

    private ShortUrl(
            final Long id,
            final String shortUrl,
            final String originalUrl,
            final LocalDateTime createdAt
    ) {
        this.id = id;
        this.shortUrl = shortUrl;
        this.originalUrl = originalUrl;
        this.createdAt = createdAt;
    }

    public static ShortUrl of(String shortUrl, String originalUrl) {
        return new ShortUrl(null, shortUrl, originalUrl, LocalDateTime.now());
    }

    public static ShortUrl restore(Long id, String shortUrl, String originalUrl) {
        return new ShortUrl(id, shortUrl, originalUrl, LocalDateTime.now());
    }

    public Long getId() {
        return id;
    }

    public String getShortUrl() {
        return shortUrl;
    }

    public String getOriginalUrl() {
        return originalUrl;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
