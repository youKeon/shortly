package com.io.shortly.click.infrastructure.persistence.jpa;

import com.io.shortly.click.domain.UrlClick;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(name = "url_clicks", indexes = {
        @Index(name = "idx_short_code", columnList = "short_code"),
        @Index(name = "idx_clicked_at", columnList = "clicked_at"),
        @Index(name = "idx_short_code_clicked_at", columnList = "short_code,clicked_at")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class UrlClickJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "short_code", nullable = false, length = 10)
    private String shortCode;

    @Column(name = "original_url", nullable = false, length = 2048)
    private String originalUrl;

    @CreationTimestamp
    @Column(name = "clicked_at", nullable = false, updatable = false)
    private LocalDateTime clickedAt;

    public UrlClick toDomain() {
        return UrlClick.restore(id, shortCode, originalUrl, clickedAt);
    }

    public static UrlClickJpaEntity fromDomain(UrlClick urlClick) {
        return new UrlClickJpaEntity(
                urlClick.getId(),
                urlClick.getShortCode(),
                urlClick.getOriginalUrl(),
                urlClick.getClickedAt()
        );
    }
}
