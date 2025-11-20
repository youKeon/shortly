package com.io.shortly.redirect.infrastructure.client;

public record UrlLookupResponse(
    String shortCode,
    String originalUrl
) {
}
