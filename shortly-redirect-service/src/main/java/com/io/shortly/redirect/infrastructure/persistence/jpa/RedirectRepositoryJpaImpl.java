package com.io.shortly.redirect.infrastructure.persistence.jpa;

import com.io.shortly.redirect.domain.Redirect;
import com.io.shortly.redirect.domain.RedirectRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

@Slf4j
@Repository
@RequiredArgsConstructor
public class RedirectRepositoryJpaImpl implements RedirectRepository {

    private final RedirectJpaRepository jpaRepository;

    @Override
    public Optional<Redirect> findByShortCode(String shortCode) {
        log.debug("[DB] Finding redirect: shortCode={}", shortCode);
        return jpaRepository.findByShortCode(shortCode)
            .map(entity -> {
                log.debug("[DB] Found: shortCode={}", shortCode);
                return entity.toDomain();
            });
    }

    @Override
    public Redirect save(Redirect redirect) {
        log.info("[DB] Saving redirect: shortCode={}", redirect.getShortCode());
        RedirectJpaEntity entity = RedirectJpaEntity.fromDomain(redirect);
        RedirectJpaEntity saved = jpaRepository.save(entity);
        log.info("[DB] Saved: shortCode={}", saved.getShortCode());
        return saved.toDomain();
    }

    @Override
    public boolean existsByShortCode(String shortCode) {
        boolean exists = jpaRepository.existsByShortCode(shortCode);
        log.debug("[DB] Exists check: shortCode={}, exists={}", shortCode, exists);
        return exists;
    }
}
