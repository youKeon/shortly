package com.io.shortly.shared.event;

import java.time.Instant;
import java.util.Objects;
import lombok.Getter;

@Getter
public abstract class BaseEvent {

    private final long eventId;
    private final EventType eventType;
    private final Instant timestamp;

    protected BaseEvent(Long eventId, EventType eventType, Instant timestamp) {
        this.eventId = eventId != null ? eventId : 0L;
        this.eventType = Objects.requireNonNull(eventType, "eventType must not be null");
        this.timestamp = timestamp != null ? timestamp : Instant.now();
    }
}
