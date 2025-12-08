package com.io.shortly.shared.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.Objects;
import lombok.Getter;

@Getter
public class UrlCreatedEvent extends BaseEvent {

    private final String shortCode;
    private final String originalUrl;
    private final Instant createdAt;

    public UrlCreatedEvent(
        final long eventId,
        final String shortCode,
        final String originalUrl,
        final Instant createdAt
    ) {
        this(eventId, EventType.URL_CREATED, null, shortCode, originalUrl, createdAt);
    }

    @JsonCreator
    public UrlCreatedEvent(
        @JsonProperty("eventId") final long eventId,
        @JsonProperty("eventType") final EventType eventType,
        @JsonProperty("timestamp") final Instant timestamp,
        @JsonProperty("shortCode") final String shortCode,
        @JsonProperty("originalUrl") final String originalUrl,
        @JsonProperty("createdAt") final Instant createdAt
    ) {
        super(eventId, eventType, timestamp);
        this.shortCode = Objects.requireNonNull(shortCode, "shortCode must not be null");
        this.originalUrl = Objects.requireNonNull(originalUrl, "originalUrl must not be null");
        this.createdAt = createdAt != null ? createdAt : Instant.now();
    }

    public static UrlCreatedEvent of(long eventId, String shortCode, String originalUrl) {
        return new UrlCreatedEvent(eventId, shortCode, originalUrl, Instant.now());
    }
}
