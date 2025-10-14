package com.io.shortly.application.dto;

import lombok.experimental.UtilityClass;

@UtilityClass
public class ShortenUrlResult {

    public record CreateShortUrlResult(String shortCode, String originalUrl) {

        public static CreateShortUrlResult of(String shortCode, String originalUrl) {
            return new CreateShortUrlResult(shortCode, originalUrl);
        }
    }

    public record ShortUrlLookupResult(Long urlId, String originalUrl, String shortCode) {

        public static ShortUrlLookupResult of(Long urlId, String originalUrl, String shortCode) {
            return new ShortUrlLookupResult(urlId, originalUrl, shortCode);
        }
    }
}

