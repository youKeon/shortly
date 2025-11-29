package com.io.shortly.redirect.application;

import com.io.shortly.redirect.domain.Redirect;

public interface UrlFetcher {

    Redirect fetchShortUrl(String shortCode);
}
