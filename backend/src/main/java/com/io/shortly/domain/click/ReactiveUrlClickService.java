package com.io.shortly.domain.click;

import reactor.core.publisher.Mono;

public interface ReactiveUrlClickService {

    Mono<Void> incrementClickCount(Long urlId);
}

