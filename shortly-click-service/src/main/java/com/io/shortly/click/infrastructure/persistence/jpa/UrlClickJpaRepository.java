package com.io.shortly.click.infrastructure.persistence.jpa;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface UrlClickJpaRepository extends JpaRepository<UrlClickJpaEntity, Long> {

    long countByShortCode(String shortCode);

    List<UrlClickJpaEntity> findByShortCodeAndClickedAtBetween(
            String shortCode,
            LocalDateTime start,
            LocalDateTime end
    );
}
