package com.io.bitly.application;

import com.io.bitly.application.dto.ShortenUrlResult.ShortUrlLookupResult;
import com.io.bitly.domain.shorturl.ShortUrl;
import com.io.bitly.domain.shorturl.ShortUrlRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UrlCacheService {

    private final ShortUrlRepository shortUrlRepository;

    @Cacheable(value = "shortUrls", key = "#shortCode")
    @Transactional(readOnly = true)
    public ShortUrlLookupResult findByShortCode(String shortCode) {
        ShortUrl shortUrl = shortUrlRepository.findByShortUrl(shortCode)
                .orElseThrow(() -> new IllegalArgumentException("Short code not found: " + shortCode));

        log.debug("URL lookup (DB query): {} -> {}", shortCode, shortUrl.getOriginalUrl());

        return ShortUrlLookupResult.of(
                shortUrl.getId(),
                shortUrl.getOriginalUrl(),
                shortUrl.getShortUrl()
        );
    }
}
