package com.io.shortly.redirect.domain;

public class RedirectCacheService {

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
        } catch (Exception ignored) {}
        l2Cache.put(redirect);
    }
}
