package com.io.bitly.application.dto;

import lombok.experimental.UtilityClass;

@UtilityClass
public class ShortenUrlCommand {

    public record CreateShortUrlCommand(String originalUrl) {

        public static CreateShortUrlCommand of(String originalUrl) {
            return new CreateShortUrlCommand(originalUrl);
        }
    }

    public record ShortUrlLookupCommand(String shortCode) {

        public static ShortUrlLookupCommand of(String shortCode) {
            return new ShortUrlLookupCommand(shortCode);
        }
    }
}
