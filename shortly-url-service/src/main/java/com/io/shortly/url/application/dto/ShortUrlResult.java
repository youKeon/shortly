package com.io.shortly.url.application.dto;

import lombok.experimental.UtilityClass;

@UtilityClass
public class ShortUrlResult {

    public record ShortenedResult(
            String shortCode,
            String originalUrl
    ) {
        public static ShortenedResult of(String shortCode, String originalUrl) {
            return new ShortenedResult(shortCode, originalUrl);
        }
    }
}
