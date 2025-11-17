package com.io.shortly.redirect.infrastructure.restclient;

public record UrlLookupResponse(
    String shortCode,
    String originalUrl
) {
}
