package com.io.shortly.shared.event;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.Getter;

@Getter
public abstract class BaseEvent {

    private final String eventId;
    private final EventType eventType;
    private final Instant timestamp;

    protected BaseEvent(EventType eventType) {
        this(null, eventType, null);
    }

    protected BaseEvent(String eventId, EventType eventType, Instant timestamp) {
        this.eventId = eventId != null ? eventId : UUID.randomUUID().toString();
        this.eventType = Objects.requireNonNull(eventType, "eventType must not be null");
        this.timestamp = timestamp != null ? timestamp : Instant.now();
    }
}
