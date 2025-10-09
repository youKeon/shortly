package com.io.bitly.infrastructure.persistence.shorturl;

import com.io.bitly.domain.shorturl.ShortUrl;
import jakarta.persistence.*;
import lombok.Getter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(name = "urls")
public class ShortUrlJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "short_url", nullable = false, unique = true, length = 10)
    private String shortUrl;

    @Column(name = "original_url", nullable = false, length = 2048)
    private String originalUrl;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected ShortUrlJpaEntity() {}

    private ShortUrlJpaEntity(
            final Long id,
            final String shortUrl,
            final String originalUrl,
            final LocalDateTime createdAt
    ) {
        this.id = id;
        this.shortUrl = shortUrl;
        this.originalUrl = originalUrl;
        this.createdAt = createdAt;
    }

    public ShortUrl toDomain() {
        return ShortUrl.restore(id, shortUrl, originalUrl);
    }

    public static ShortUrlJpaEntity fromDomain(ShortUrl shortUrl) {
        return new ShortUrlJpaEntity(
                shortUrl.getId(),
                shortUrl.getShortUrl(),
                shortUrl.getOriginalUrl(),
                shortUrl.getCreatedAt()
        );
    }
}
