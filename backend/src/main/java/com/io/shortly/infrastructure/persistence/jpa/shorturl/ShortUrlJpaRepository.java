package com.io.shortly.infrastructure.persistence.jpa.shorturl;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShortUrlJpaRepository extends JpaRepository<ShortUrlJpaEntity, Long> {

    Optional<ShortUrlJpaEntity> findByShortUrl(String shortCode);

    boolean existsByShortUrl(String shortCode);
}

