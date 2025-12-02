package com.io.shortly.redirect.domain;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RedirectCacheService {
    private static final Logger log = LoggerFactory.getLogger(RedirectCacheService.class);

    private final RedirectCache l1Cache;
    private final RedirectCache l2Cache;

    public RedirectCacheService(
            RedirectCache l1Cache,
            RedirectCache l2Cache
    ) {
        this.l1Cache = l1Cache;
        this.l2Cache = l2Cache;
    }

    public Redirect getRedirect(String shortCode) {
        return l1Cache.get(shortCode)
                .orElseThrow(() -> new ShortCodeNotFoundException(shortCode));
    }

    public void put(Redirect redirect) {
        try {
            l1Cache.put(redirect);
        } catch (Exception e) {
            log.warn("[MultiLayerCache] L1 Put Failed: {}", redirect.getShortCode(), e);
        }
        l2Cache.put(redirect);
    }
}
