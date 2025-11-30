package com.io.shortly.redirect.domain;

import java.util.Optional;

import java.util.function.Function;

public interface RedirectCache {

    Optional<Redirect> get(String shortCode);

    Redirect get(String shortCode, Function<String, Redirect> loader);

    void put(Redirect redirect);
}
