package com.io.shortly.redirect.domain;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RedirectCacheService {

    private static final Logger log = LoggerFactory.getLogger(RedirectCacheService.class);

    private final RedirectCache l1Cache;
    private final RedirectCache l2Cache;
    private final UrlFetcher urlFetcher;

    public RedirectCacheService(
            RedirectCache l1Cache,
        RedirectCache l2Cache,
        UrlFetcher urlFetcher
    ) {
        this.l1Cache = l1Cache;
        this.l2Cache = l2Cache;
        this.urlFetcher = urlFetcher;
    }

    public Redirect getRedirect(String shortCode) {
        return l1Cache.getOrLoad(shortCode, () ->
            // L1 miss -> L2
            l2Cache.get(shortCode)
                // L2 miss → HTTP(DB)
                .orElseGet(() -> {
                    Redirect redirect = urlFetcher.fetchShortUrl(shortCode);
                    l2Cache.put(redirect);
                    return redirect;
                })
        );
    }

    public void put(Redirect redirect) {
        try {
            l1Cache.put(redirect);
            l2Cache.put(redirect);
        } catch (Exception e) {
            log.error("[Cache] 캐시 저장 실패: shortCode={}, error={}",
                redirect.getShortCode(),
                e.getMessage()
            );
        }
    }
}
