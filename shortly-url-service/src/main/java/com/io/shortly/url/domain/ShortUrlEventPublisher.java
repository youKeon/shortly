package com.io.shortly.url.domain;

import com.io.shortly.shared.event.UrlCreatedEvent;

public interface ShortUrlEventPublisher {

    void publishUrlCreated(UrlCreatedEvent event);
}

