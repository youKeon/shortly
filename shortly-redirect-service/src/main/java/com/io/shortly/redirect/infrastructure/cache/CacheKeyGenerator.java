package com.io.shortly.redirect.infrastructure.cache;

import lombok.experimental.UtilityClass;

@UtilityClass
public class CacheKeyGenerator {

    public String generateCacheKey(CacheLayer layer, String shortCode) {
        return layer.getKeyPrefix() + shortCode;
    }
}
