package com.io.shortly.redirect.mvc.domain;

import com.io.shortly.shared.event.UrlClickedEvent;

public interface RedirectEventPublisher {

    void publishUrlClicked(UrlClickedEvent event);
}
