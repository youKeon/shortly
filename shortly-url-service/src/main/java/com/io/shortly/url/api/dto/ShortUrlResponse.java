package com.io.shortly.url.api.dto;

import com.io.shortly.url.application.dto.ShortUrlResult.ShortenedResult;
import lombok.experimental.UtilityClass;

@UtilityClass
public class ShortUrlResponse {

    public record ShortenedResponse(
            String shortCode,
            String originalUrl
    ) {
        public static ShortenedResponse of(ShortenedResult result) {
            return new ShortenedResponse(result.shortCode(), result.originalUrl());
        }
    }

    public record GetShortUrlResponse(
            String shortCode,
            String originalUrl
    ) {
        public static GetShortUrlResponse of(String shortCode, String originalUrl) {
            return new GetShortUrlResponse(shortCode, originalUrl);
        }
    }
}
