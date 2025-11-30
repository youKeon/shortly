package com.io.shortly.redirect.domain;

public interface UrlFetcher {

    Redirect fetchShortUrl(String shortCode);
}
