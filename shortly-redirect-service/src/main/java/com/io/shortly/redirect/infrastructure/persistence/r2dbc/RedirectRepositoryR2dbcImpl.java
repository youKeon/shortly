package com.io.shortly.redirect.infrastructure.persistence.r2dbc;

import com.io.shortly.redirect.domain.Redirect;
import com.io.shortly.redirect.domain.RedirectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Slf4j
@Repository
@RequiredArgsConstructor
public class RedirectRepositoryR2dbcImpl implements RedirectRepository {

    private final RedirectR2dbcRepository r2dbcRepository;

    @Override
    public Mono<Redirect> findByShortCode(String shortCode) {
        return r2dbcRepository.findByShortCode(shortCode)
                .map(RedirectR2dbcEntity::toDomain)
                .doOnNext(redirect -> log.debug("[DB] Found: shortCode={}", shortCode))
                .doOnError(error -> log.error("[DB] Query failed: shortCode={}", shortCode, error));
    }

    @Override
    public Mono<Redirect> save(Redirect redirect) {
        return r2dbcRepository.save(RedirectR2dbcEntity.fromDomain(redirect))
                .map(RedirectR2dbcEntity::toDomain)
                .doOnNext(saved -> log.info("[DB] Saved: shortCode={}", saved.getShortCode()))
                .doOnError(error -> log.error("[DB] Save failed: shortCode={}", redirect.getShortCode(), error));
    }

    @Override
    public Mono<Boolean> existsByShortCode(String shortCode) {
        return r2dbcRepository.existsByShortCode(shortCode);
    }
}
