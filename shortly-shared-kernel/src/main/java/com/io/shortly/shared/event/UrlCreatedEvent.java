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
        final String shortCode,
        final String originalUrl,
        final Instant createdAt
    ) {
        this(null, EventType.URL_CREATED, null, shortCode, originalUrl, createdAt);
    }

    @JsonCreator
    public UrlCreatedEvent(
        @JsonProperty("eventId") final String eventId,
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

    public static UrlCreatedEvent of(String shortCode, String originalUrl) {
        return new UrlCreatedEvent(shortCode, originalUrl, Instant.now());
    }
}
