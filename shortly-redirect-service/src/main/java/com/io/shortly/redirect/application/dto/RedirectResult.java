package com.io.shortly.redirect.application.dto;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

public class RedirectResult {

    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Redirect {
        private String originalUrl;

        public static Redirect of(String originalUrl) {
            return new Redirect(originalUrl);
        }
    }
}
