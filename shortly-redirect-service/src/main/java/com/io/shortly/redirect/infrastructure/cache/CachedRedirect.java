package com.io.shortly.redirect.infrastructure.cache;

import com.io.shortly.redirect.domain.Redirect;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

public record CachedRedirect(
        String shortCode,
        String targetUrl,
        Instant createdAt
) {

    private static final ZoneOffset CACHE_ZONE = ZoneOffset.UTC;

    public static CachedRedirect from(Redirect model) {
        return new CachedRedirect(
            model.getShortCode(),
            model.getTargetUrl(),
            model.getCreatedAt()
                .atOffset(CACHE_ZONE)
                .toInstant()
        );
    }

    public Redirect toDomain() {
        return Redirect.restore(
            null,
            shortCode,
            targetUrl,
            LocalDateTime.ofInstant(createdAt, CACHE_ZONE)
        );
    }
}
