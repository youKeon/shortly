package com.io.shortly.domain.shorturl;

import java.util.Optional;

public interface ShortUrlCache {

    Optional<ShortUrl> get(String shortCode);

    void put(ShortUrl shortUrl);
}

