package com.io.shortly.url.api.dto;

import lombok.experimental.UtilityClass;

@UtilityClass
public class ShortUrlResponse {

    public record ShortenedResponse(
            String shortCode,
            String originalUrl
    ) {}
}
