package com.io.bitly.infrastructure.persistence.click;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UrlClickJpaRepository extends JpaRepository<UrlClickJpaEntity, Long> {
}
