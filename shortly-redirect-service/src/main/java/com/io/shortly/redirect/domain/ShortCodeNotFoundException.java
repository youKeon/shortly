package com.io.shortly.redirect.domain;

public class ShortCodeNotFoundException extends RuntimeException {

    private final String shortCode;

    public ShortCodeNotFoundException(String shortCode) {
        super(String.format("Short code not found: %s", shortCode));
        this.shortCode = shortCode;
    }

    public String getShortCode() {
        return shortCode;
    }
}
