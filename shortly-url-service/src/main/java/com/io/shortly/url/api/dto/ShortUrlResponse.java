package com.io.shortly.url.api.dto;

import lombok.experimental.UtilityClass;

@UtilityClass
public class ShortUrlResponse {

    public record ShortenedResponse(
            String shortCode,
            String originalUrl
    ) {}

    public record GetShortUrlResponse(
            String shortCode,
            String originalUrl
    ) {
        public static GetShortUrlResponse of(String shortCode, String originalUrl) {
            return new GetShortUrlResponse(shortCode, originalUrl);
        }
    }
}
