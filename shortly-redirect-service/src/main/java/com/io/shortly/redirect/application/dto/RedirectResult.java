package com.io.shortly.redirect.application.dto;

import lombok.experimental.UtilityClass;

@UtilityClass
public class RedirectResult {

    public record RedirectLookupResult(String originalUrl) {

        public static RedirectLookupResult of(String originalUrl) {
            return new RedirectLookupResult(originalUrl);
        }
    }
}
