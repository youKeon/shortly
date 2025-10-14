package com.io.shortly.domain.shorturl;

import com.io.shortly.application.dto.ShortenUrlResult.ShortUrlLookupResult;
import java.util.Optional;

public interface ShortUrlCache {

    Optional<ShortUrlLookupResult> get(String shortCode);

    void put(ShortUrl shortUrl);
}
