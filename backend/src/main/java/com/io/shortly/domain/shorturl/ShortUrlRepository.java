package com.io.shortly.domain.shorturl;

import java.util.Optional;

public interface ShortUrlRepository {

    ShortUrl save(ShortUrl shortUrl);

    Optional<ShortUrl> findByShortUrl(String shortCode);

    boolean existsByShortUrl(String shortCode);
}

