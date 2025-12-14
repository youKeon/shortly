package com.io.shortly.test.unit.url.mock;

import com.io.shortly.shared.event.UrlCreatedEvent;
import com.io.shortly.url.domain.ShortUrlEventPublisher;
import java.util.ArrayList;
import java.util.List;

/**
 * ShortUrlEventPublisher의 테스트용 Fake 구현체
 * 이벤트 발행을 기록하고 검증 가능하도록 함
 */
public class FakeShortUrlEventPublisher implements ShortUrlEventPublisher {

    private final List<UrlCreatedEvent> publishedEvents = new ArrayList<>();
    private int publishCallCount = 0;

    @Override
    public void publishUrlCreated(UrlCreatedEvent event) {
        publishCallCount++;
        publishedEvents.add(event);
    }

    // 테스트 검증용 메서드
    public int getPublishCallCount() {
        return publishCallCount;
    }

    public List<UrlCreatedEvent> getPublishedEvents() {
        return new ArrayList<>(publishedEvents);
    }

    public UrlCreatedEvent getLastPublishedEvent() {
        return publishedEvents.isEmpty() ? null : publishedEvents.get(publishedEvents.size() - 1);
    }

    public void clear() {
        publishedEvents.clear();
        publishCallCount = 0;
    }
}
