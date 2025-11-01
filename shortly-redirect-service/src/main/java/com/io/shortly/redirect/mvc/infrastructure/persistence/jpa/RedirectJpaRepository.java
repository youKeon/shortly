package com.io.shortly.redirect.mvc.infrastructure.persistence.jpa;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RedirectJpaRepository extends JpaRepository<RedirectJpaEntity, String> {

    Optional<RedirectJpaEntity> findByShortCode(String shortCode);

    boolean existsByShortCode(String shortCode);
}
