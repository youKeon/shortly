package com.io.shortly.click.domain;

import java.time.LocalDateTime;
import java.util.Objects;

public class UrlClick {

    private final Long id;
    private final long eventId;
    private final String shortCode;
    private final String originalUrl;
    private final LocalDateTime clickedAt;

    private UrlClick(
            Long id,
            long eventId,
            String shortCode,
            String originalUrl,
            LocalDateTime clickedAt
    ) {
        this.id = id;
        this.eventId = eventId;
        this.shortCode = shortCode;
        this.originalUrl = originalUrl;
        this.clickedAt = clickedAt;
    }

    public static UrlClick create(
            long eventId,
            String shortCode,
            String originalUrl
    ) {
        return new UrlClick(null, eventId, shortCode, originalUrl, LocalDateTime.now());
    }

    public static UrlClick restore(
            Long id,
            long eventId,
            String shortCode,
            String originalUrl,
            LocalDateTime clickedAt
    ) {
        return new UrlClick(id, eventId, shortCode, originalUrl, clickedAt);
    }

    public Long getId() {
        return id;
    }

    public long getEventId() {
        return eventId;
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
        return Objects.equals(eventId, urlClick.eventId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventId);
    }
}
