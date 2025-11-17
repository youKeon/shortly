package com.io.shortly.redirect.domain;

import java.util.Optional;

public interface RedirectCacheService {

    Optional<Redirect> get(String shortCode);

    void put(Redirect redirect);
}
