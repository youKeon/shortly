package com.io.shortly.url.application.dto;

import lombok.experimental.UtilityClass;

@UtilityClass
public class ShortUrlCommand {

    public record ShortenCommand(String originalUrl) {
        public static ShortenCommand of(String originalUrl) {
            return new ShortenCommand(originalUrl);
        }
    }
}
