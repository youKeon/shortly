package com.io.shortly.redirect.domain;

import reactor.core.publisher.Mono;

public interface RedirectRepository {

    Mono<Redirect> findByShortCode(String shortCode);

    Mono<Redirect> save(Redirect redirect);

    Mono<Boolean> existsByShortCode(String shortCode);
}
