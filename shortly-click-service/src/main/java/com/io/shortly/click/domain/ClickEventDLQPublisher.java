package com.io.shortly.click.domain;

import com.io.shortly.shared.event.UrlClickedEvent;

public interface ClickEventDLQPublisher {

    void publishToDLQ(UrlClickedEvent event, Exception exception, int retryCount);
}
