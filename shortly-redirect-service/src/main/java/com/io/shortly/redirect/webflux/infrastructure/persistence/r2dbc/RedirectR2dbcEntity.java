package com.io.shortly.redirect.webflux.infrastructure.persistence.r2dbc;

import com.io.shortly.redirect.webflux.domain.Redirect;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Getter
@Table("redirects")
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RedirectR2dbcEntity {

    @Id
    private Long id;

    @Column("short_code")
    private String shortCode;

    @Column("target_url")
    private String targetUrl;

    @CreatedDate
    @Column("created_at")
    private LocalDateTime createdAt;

    public static RedirectR2dbcEntity fromDomain(Redirect redirect) {
        return new RedirectR2dbcEntity(
                redirect.getId(),
                redirect.getShortCode(),
                redirect.getTargetUrl(),
                redirect.getCreatedAt()
        );
    }

    public Redirect toDomain() {
        return Redirect.restore(id, shortCode, targetUrl, createdAt);
    }
}
