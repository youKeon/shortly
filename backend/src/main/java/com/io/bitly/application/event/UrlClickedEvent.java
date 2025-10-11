package com.io.bitly.application.event;

public record UrlClickedEvent(
    Long urlId,
    String shortCode
) {
    public static UrlClickedEvent of(Long urlId, String shortCode) {
        return new UrlClickedEvent(urlId, shortCode);
    }
}
