package com.io.shortly.url.domain.event;

public record UrlCreatedDomainEvent(String shortCode, String originalUrl) {

    public static UrlCreatedDomainEvent of(String shortCode, String originalUrl) {
        return new UrlCreatedDomainEvent(shortCode, originalUrl);
    }
}
