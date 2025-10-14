package com.io.shortly.infrastructure.persistence.jpa.click;

import com.io.shortly.domain.click.UrlClick;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import org.hibernate.annotations.CreationTimestamp;

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


