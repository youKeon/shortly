package com.io.shortly.redirect.domain;

import reactor.core.publisher.Mono;

public interface RedirectCache {

    Mono<Void> put(Redirect redirect);

    Mono<Redirect> get(String shortCode);

    Mono<Void> evict(String shortCode);
}
