package com.io.shortly.url.infrastructure.persistence.jpa.url;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ShortUrlJpaRepository extends JpaRepository<ShortUrlJpaEntity, Long> {

    Optional<ShortUrlJpaEntity> findByShortCode(String shortCode);

    boolean existsByShortCode(String shortCode);
}
