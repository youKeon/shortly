package com.io.shortly.redirect.domain;

import java.util.Optional;
import java.util.function.Supplier;

public interface RedirectCache {

    Optional<Redirect> get(String shortCode);

    Redirect getOrLoad(String shortCode, Supplier<Redirect> loader);

    void put(Redirect redirect);
}
