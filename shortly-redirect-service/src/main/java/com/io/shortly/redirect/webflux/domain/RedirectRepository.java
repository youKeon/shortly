package com.io.shortly.redirect.webflux.domain;

import reactor.core.publisher.Mono;

public interface RedirectRepository {

    Mono<Redirect> findByShortCode(String shortCode);

    Mono<Redirect> save(Redirect redirect);

    Mono<Boolean> existsByShortCode(String shortCode);
}
