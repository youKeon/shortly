package com.io.shortly.redirect.infrastructure.cache;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CacheLayer {
    L1("redirect:l1:"),
    L2("redirect:l2:");

    private final String keyPrefix;
}
