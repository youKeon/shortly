package com.io.shortly.redirect.mvc.infrastructure.persistence.jpa;

import com.io.shortly.redirect.mvc.domain.Redirect;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "redirect")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class RedirectJpaEntity {

    @Id
    @Column(name = "short_code", nullable = false, length = 10)
    private String shortCode;

    @Column(name = "target_url", nullable = false, length = 2048)
    private String targetUrl;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static RedirectJpaEntity fromDomain(Redirect redirect) {
        return new RedirectJpaEntity(
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
