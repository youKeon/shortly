package com.io.shortly.redirect.webflux.domain;

import reactor.core.publisher.Mono;

public interface RedirectCache {

    Mono<Void> put(Redirect redirect);

    Mono<Redirect> get(String shortCode);

    Mono<Void> evict(String shortCode);
}
