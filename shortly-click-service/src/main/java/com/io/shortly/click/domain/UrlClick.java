package com.io.shortly.click.domain;

import java.time.LocalDateTime;
import java.util.Objects;

public class UrlClick {

    private final Long id;
    private final String shortCode;
    private final String originalUrl;
    private final LocalDateTime clickedAt;

    private UrlClick(
            Long id,
            String shortCode,
            String originalUrl,
            LocalDateTime clickedAt
    ) {
        this.id = id;
        this.shortCode = shortCode;
        this.originalUrl = originalUrl;
        this.clickedAt = clickedAt;
    }

    public static UrlClick create(
            String shortCode,
            String originalUrl
    ) {
        return new UrlClick(null, shortCode, originalUrl, LocalDateTime.now());
    }

    public static UrlClick restore(
            Long id,
            String shortCode,
            String originalUrl,
            LocalDateTime clickedAt
    ) {
        return new UrlClick(id, shortCode, originalUrl, clickedAt);
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

    public LocalDateTime getClickedAt() {
        return clickedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UrlClick urlClick = (UrlClick) o;
        return Objects.equals(id, urlClick.id) &&
               Objects.equals(shortCode, urlClick.shortCode) &&
               Objects.equals(clickedAt, urlClick.clickedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, shortCode, clickedAt);
    }
}
