package com.io.shortly.shared.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.Objects;
import lombok.Getter;

@Getter
public final class UrlClickedEvent extends BaseEvent {

    private final String shortCode;
    private final String originalUrl;

    public UrlClickedEvent(
        final long eventId,
        final String shortCode,
        final String originalUrl
    ) {
        this(eventId, EventType.URL_CLICKED, null, shortCode, originalUrl);
    }

    @JsonCreator
    public UrlClickedEvent(
        @JsonProperty("eventId") final long eventId,
        @JsonProperty("eventType") final EventType eventType,
        @JsonProperty("timestamp") final Instant timestamp,
        @JsonProperty("shortCode") final String shortCode,
        @JsonProperty("originalUrl") final String originalUrl
    ) {
        super(eventId, eventType, timestamp);
        this.shortCode = Objects.requireNonNull(shortCode, "shortCode must not be null");
        this.originalUrl = Objects.requireNonNull(originalUrl, "originalUrl must not be null");
    }

    public static UrlClickedEvent of(long eventId, String shortCode, String originalUrl) {
        return new UrlClickedEvent(eventId, shortCode, originalUrl);
    }
}
