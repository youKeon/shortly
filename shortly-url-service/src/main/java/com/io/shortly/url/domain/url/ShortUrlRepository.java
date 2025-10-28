package com.io.shortly.url.domain.url;

import java.util.Optional;

public interface ShortUrlRepository {

    void save(ShortUrl shortUrl);

    Optional<ShortUrl> findByShortCode(String shortCode);

    boolean existsByShortCode(String shortCode);
}
