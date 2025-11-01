package com.io.shortly.redirect.mvc.infrastructure.persistence.jpa;

import com.io.shortly.redirect.mvc.domain.Redirect;
import com.io.shortly.redirect.mvc.domain.RedirectRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

@Slf4j
@Repository("mvcRedirectRepository")
@RequiredArgsConstructor
public class RedirectRepositoryJpaImpl implements RedirectRepository {

    private final RedirectJpaRepository jpaRepository;

    @Override
    public Optional<Redirect> findByShortCode(String shortCode) {
        log.debug("[DB-MVC] Finding redirect: shortCode={}", shortCode);
        return jpaRepository.findByShortCode(shortCode)
            .map(entity -> {
                log.debug("[DB-MVC] Found: shortCode={}", shortCode);
                return entity.toDomain();
            });
    }

    @Override
    public Redirect save(Redirect redirect) {
        log.info("[DB-MVC] Saving redirect: shortCode={}", redirect.getShortCode());
        RedirectJpaEntity entity = RedirectJpaEntity.fromDomain(redirect);
        RedirectJpaEntity saved = jpaRepository.save(entity);
        log.info("[DB-MVC] Saved: shortCode={}", saved.getShortCode());
        return saved.toDomain();
    }

    @Override
    public boolean existsByShortCode(String shortCode) {
        boolean exists = jpaRepository.existsByShortCode(shortCode);
        log.debug("[DB-MVC] Exists check: shortCode={}, exists={}", shortCode, exists);
        return exists;
    }
}
