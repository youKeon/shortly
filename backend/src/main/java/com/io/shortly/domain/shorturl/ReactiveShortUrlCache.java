package com.io.shortly.domain.shorturl;

import reactor.core.publisher.Mono;

public interface ReactiveShortUrlCache {

    Mono<ShortUrl> get(String shortCode);

    Mono<Void> put(ShortUrl shortUrl);
}

