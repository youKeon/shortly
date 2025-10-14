package com.io.shortly.api.dto;

import lombok.experimental.UtilityClass;

@UtilityClass
public final class ShortenResponse {

    public record CreateShortUrlResponse(String shortCode) {

        public static CreateShortUrlResponse of(String shortCode) {
            return new CreateShortUrlResponse(shortCode);
        }
    }
}

