package com.io.shortly.redirect.domain;

import java.util.Optional;

public interface RedirectRepository {

    Optional<Redirect> findByShortCode(String shortCode);

    Redirect save(Redirect redirect);

    boolean existsByShortCode(String shortCode);
}
