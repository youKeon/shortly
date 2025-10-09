package com.io.bitly.infrastructure.persistence.click;

import com.io.bitly.domain.click.UrlClick;
import jakarta.persistence.*;
import lombok.Getter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(name = "url_clicks")
public class UrlClickJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "url_id", nullable = false)
    private Long urlId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected UrlClickJpaEntity() {
    }

    public UrlClickJpaEntity(
            final Long id,
            final Long urlId,
            final LocalDateTime createdAt
    ) {
        this.id = id;
        this.urlId = urlId;
        this.createdAt = createdAt;
    }

    public UrlClick toDomain() {
        return UrlClick.restore(id, urlId, createdAt);
    }

    public static UrlClickJpaEntity fromDomain(UrlClick urlClick) {
        return new UrlClickJpaEntity(
                urlClick.getId(),
                urlClick.getUrlId(),
                urlClick.getCreatedAt()
        );
    }
}
