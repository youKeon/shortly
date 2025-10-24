package com.io.shortly.redirect.infrastructure.persistence.r2dbc;

import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Mono;

public interface RedirectR2dbcRepository extends R2dbcRepository<RedirectR2dbcEntity, Long> {

    Mono<RedirectR2dbcEntity> findByShortCode(String shortCode);

    Mono<Boolean> existsByShortCode(String shortCode);
}
