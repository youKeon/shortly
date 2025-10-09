package com.io.bitly.infrastructure.persistence.shorturl;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ShortUrlJpaRepository extends JpaRepository<ShortUrlJpaEntity, Long> {

    Optional<ShortUrlJpaEntity> findByShortUrl(String shortCode);

    boolean existsByShortUrl(String shortCode);
}
