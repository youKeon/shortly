package com.io.shortly.redirect.domain;

import java.util.Optional;

public interface RedirectCache {

    Optional<Redirect> get(String shortCode);

    void put(Redirect redirect);

    void evict(String shortCode);
}
