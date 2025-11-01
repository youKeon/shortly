package com.io.shortly.redirect.webflux.domain;

import com.io.shortly.shared.event.UrlClickedEvent;

public interface RedirectEventPublisher {

    void publishUrlClicked(UrlClickedEvent event);
}
