package com.io.shortly.redirect.infrastructure.cache;

import com.io.shortly.redirect.domain.RedirectCache;
import com.io.shortly.redirect.domain.RedirectCacheService;
import com.io.shortly.redirect.domain.UrlFetcher;
import com.io.shortly.redirect.infrastructure.cache.caffeine.RedirectCacheCaffeineImpl;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CacheConfig {
    @Bean
    public RedirectCacheService redirectCacheService(
        @Qualifier("caffeineRedirectCache") RedirectCacheCaffeineImpl l1Cache,
        @Qualifier("redisCache") RedirectCache l2Cache,
        UrlFetcher urlFetcher
    ) {
        return new RedirectCacheService(l1Cache, l2Cache, urlFetcher);
    }
}
