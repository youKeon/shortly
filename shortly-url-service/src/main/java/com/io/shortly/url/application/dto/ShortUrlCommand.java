package com.io.shortly.url.application.dto;

import lombok.experimental.UtilityClass;

@UtilityClass
public class ShortUrlCommand {

    public record ShortenCommand(String originalUrl, String customCode) {
        public static ShortenCommand of(String originalUrl, String customCode) {
            return new ShortenCommand(originalUrl, customCode);
        }
    }

    public record FindCommand(String shortCode) {
        public static FindCommand of(String shortCode) {
            return new FindCommand(shortCode);
        }
    }
}
