package com.io.shortly.redirect.infrastructure.redis.cache;

import lombok.experimental.UtilityClass;

@UtilityClass
public class CacheKeyGenerator {

    public String generateCacheKey(CacheLayer layer, String shortCode) {
        return layer.getKeyPrefix() + shortCode;
    }
}
