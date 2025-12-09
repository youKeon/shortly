package com.io.shortly.redirect.domain;

import java.time.LocalDateTime;
import java.util.Objects;
import org.springframework.util.Assert;

public class Redirect {

    private final Long eventId;
    private final String shortCode;
    private final String targetUrl;
    private final LocalDateTime createdAt;

    private Redirect(
            final Long eventId,
            final String shortCode,
            final String targetUrl,
            final LocalDateTime createdAt
    ) {
        Assert.hasText(shortCode, "Short code must not be blank");
        Assert.hasText(targetUrl, "Target URL must not be blank");
        Assert.notNull(createdAt, "Created at must not be null");

        this.eventId = eventId;
        this.shortCode = shortCode;
        this.targetUrl = targetUrl;
        this.createdAt = createdAt;
    }

    public static Redirect create(Long eventId, String shortCode, String targetUrl) {
        return new Redirect(eventId, shortCode, targetUrl, LocalDateTime.now());
    }

    public static Redirect create(String shortCode, String targetUrl) {
        return new Redirect(null, shortCode, targetUrl, LocalDateTime.now());
    }

    public static Redirect of(long eventId, String shortCode, String targetUrl, LocalDateTime createdAt) {
        return new Redirect(eventId, shortCode, targetUrl, createdAt);
    }

    public Long getEventId() {
        return eventId;
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
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        Redirect redirect = (Redirect) o;
        return Objects.equals(shortCode, redirect.shortCode) &&
                Objects.equals(targetUrl, redirect.targetUrl);
    }

    @Override
    public int hashCode() {
        return Objects.hash(shortCode, targetUrl);
    }

    @Override
    public String toString() {
        return "Redirect{" +
                "shortCode='" + shortCode + '\'' +
                ", targetUrl='" + targetUrl + '\'' +
                ", createdAt=" + createdAt +
                '}';
    }
}
