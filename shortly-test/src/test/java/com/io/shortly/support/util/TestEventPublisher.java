package com.io.shortly.support.util;

import com.io.shortly.shared.event.BaseEvent;

import java.util.ArrayList;
import java.util.List;

public class TestEventPublisher {

    private final List<BaseEvent> publishedEvents = new ArrayList<>();

    public void publish(BaseEvent event) {
        publishedEvents.add(event);
    }

    public List<BaseEvent> getPublishedEvents() {
        return new ArrayList<>(publishedEvents);
    }

    public <T extends BaseEvent> List<T> getEventsOfType(Class<T> eventType) {
        return publishedEvents.stream()
            .filter(eventType::isInstance)
            .map(eventType::cast)
            .toList();
    }

    public int getEventCount() {
        return publishedEvents.size();
    }

    public void clear() {
        publishedEvents.clear();
    }

    public boolean hasEvent(Class<? extends BaseEvent> eventType) {
        return publishedEvents.stream()
            .anyMatch(eventType::isInstance);
    }
}
