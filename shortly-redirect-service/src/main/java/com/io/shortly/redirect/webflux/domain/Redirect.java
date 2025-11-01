package com.io.shortly.redirect.webflux.domain;

import java.time.LocalDateTime;
import java.util.Objects;

public class Redirect {

    private final Long id;
    private final String shortCode;
    private final String targetUrl;
    private final LocalDateTime createdAt;

    private Redirect(
        final Long id,
        final String shortCode,
        final String targetUrl,
        final LocalDateTime createdAt
    ) {
        this.id = id;
        this.shortCode = shortCode;
        this.targetUrl = targetUrl;
        this.createdAt = createdAt;
    }

    public static Redirect create(String shortCode, String targetUrl) {
        return new Redirect(null, shortCode, targetUrl, LocalDateTime.now());
    }

    public static Redirect restore(Long id, String shortCode, String targetUrl, LocalDateTime createdAt) {
        return new Redirect(id, shortCode, targetUrl, createdAt);
    }

    public Long getId() {
        return id;
    }

    public String getShortCode() {
        return shortCode;
    }

    public String getTargetUrl() {
        return targetUrl;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Redirect redirect = (Redirect) o;
        return Objects.equals(shortCode, redirect.shortCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(shortCode);
    }
}
