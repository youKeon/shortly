package com.io.shortly.url.domain.event;

import com.io.shortly.shared.event.UrlCreatedEvent;

public interface UrlEventPublisher {

    void publishUrlCreated(UrlCreatedEvent event);
}
