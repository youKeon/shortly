package com.io.shortly.domain.click;

import java.time.LocalDateTime;

public class UrlClick {

    private final Long id;
    private final Long urlId;
    private final LocalDateTime createdAt;

    private UrlClick(
            final Long id,
            final Long urlId,
            final LocalDateTime createdAt
    ) {
        this.id = id;
        this.urlId = urlId;
        this.createdAt = createdAt;
    }

    public static UrlClick of(Long urlId) {
        return new UrlClick(null, urlId, LocalDateTime.now());
    }

    public static UrlClick restore(Long id, Long urlId, LocalDateTime createdAt) {
        return new UrlClick(id, urlId, createdAt);
    }

    public Long getId() {
        return id;
    }

    public Long getUrlId() {
        return urlId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}

