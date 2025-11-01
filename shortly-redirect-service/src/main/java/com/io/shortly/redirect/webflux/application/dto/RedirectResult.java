package com.io.shortly.redirect.webflux.application.dto;

import lombok.experimental.UtilityClass;

@UtilityClass
public class RedirectResult {

    public record Redirect(String originalUrl) {
        public static Redirect of(String originalUrl) {
            return new Redirect(originalUrl);
        }
    }
}
