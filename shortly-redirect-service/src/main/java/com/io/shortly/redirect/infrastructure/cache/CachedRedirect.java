package com.io.shortly.redirect.infrastructure.cache;

import com.io.shortly.redirect.domain.Redirect;
import java.io.Serializable;
import java.time.LocalDateTime;

public record CachedRedirect(
    long eventId,
    String shortCode,
    String targetUrl,
    LocalDateTime createdAt
) implements Serializable {

    private static final long serialVersionUID = 1L;

    public static CachedRedirect from(Redirect redirect) {
        return new CachedRedirect(
            redirect.getEventId(),
            redirect.getShortCode(),
            redirect.getTargetUrl(),
            redirect.getCreatedAt()
        );
    }

    public Redirect toDomain() {
        return Redirect.of(
            this.eventId,
            this.shortCode,
            this.targetUrl,
            this.createdAt
        );
    }
}
