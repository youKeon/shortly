package com.io.shortly.click.domain;

import java.time.LocalDateTime;
import java.util.List;

public interface UrlClickRepository {

    UrlClick save(UrlClick urlClick);

    void saveAll(List<UrlClick> urlClicks);

    long countByShortCode(String shortCode);

    List<UrlClick> findByShortCodeAndClickedAtBetween(
            String shortCode,
            LocalDateTime start,
            LocalDateTime end
    );
}
