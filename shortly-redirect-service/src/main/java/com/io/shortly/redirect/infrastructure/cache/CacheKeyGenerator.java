package com.io.shortly.redirect.infrastructure.cache;

import lombok.experimental.UtilityClass;

@UtilityClass
public class CacheKeyGenerator {

    public String generateCacheKey(CacheLayer layer, String shortCode) {
        return layer.getKeyPrefix() + shortCode;
    }

    public String extractShortCode(CacheLayer layer, String key) {
        if (key.startsWith(layer.getKeyPrefix())) {
            return key.substring(layer.getKeyPrefix().length());
        }
        throw new IllegalArgumentException("Invalid cache key for layer " + layer + ": " + key);
    }
}
