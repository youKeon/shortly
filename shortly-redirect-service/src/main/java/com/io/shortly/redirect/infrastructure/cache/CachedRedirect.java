package com.io.shortly.redirect.infrastructure.cache;

import com.io.shortly.redirect.domain.Redirect;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class CachedRedirect implements Serializable {

    private static final long serialVersionUID = 1L;

    private String shortCode;
    private String targetUrl;
    private LocalDateTime createdAt;

    public static CachedRedirect from(Redirect redirect) {
        return new CachedRedirect(
            redirect.getShortCode(),
            redirect.getTargetUrl(),
            redirect.getCreatedAt()
        );
    }

    public Redirect toDomain() {
        return Redirect.of(
            this.shortCode,
            this.targetUrl,
            this.createdAt
        );
    }
}
