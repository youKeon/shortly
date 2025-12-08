package com.io.shortly.redirect.infrastructure.cache.caffeine;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.io.shortly.redirect.domain.Redirect;
import com.io.shortly.redirect.domain.RedirectCache;
import com.io.shortly.redirect.domain.UrlFetcher;
import com.io.shortly.redirect.infrastructure.cache.CacheKeyGenerator;
import com.io.shortly.redirect.infrastructure.cache.CacheLayer;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CaffeineConfig {

    private final long l1MaxSize;
    private final Duration l1Ttl;

    public CaffeineConfig(
            @Value("${shortly.cache.l1.max-size:100000}") long l1MaxSize,
            @Value("${shortly.cache.l1.ttl:10m}") Duration l1Ttl
    ) {
        this.l1MaxSize = l1MaxSize;
        this.l1Ttl = l1Ttl;
    }

    @Bean
    public LoadingCache<String, Redirect> caffeineCache(
            @Qualifier("redisCache") RedirectCache l2Cache,
            UrlFetcher urlFetcher) {
        return Caffeine.newBuilder()
                .maximumSize(l1MaxSize)
                .refreshAfterWrite(l1Ttl)
                .recordStats()
                .build(key -> {
                    String shortCode = CacheKeyGenerator.extractShortCode(CacheLayer.L1, key);

                    return l2Cache.get(shortCode)
                            .orElseGet(() -> {
                                Redirect redirect = urlFetcher.fetchShortUrl(shortCode);
                                l2Cache.put(redirect);
                                return redirect;
                            });
                });
    }
}
