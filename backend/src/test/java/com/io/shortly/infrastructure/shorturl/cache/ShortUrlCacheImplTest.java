package com.io.shortly.infrastructure.shorturl.cache;

import static org.assertj.core.api.Assertions.assertThat;

import com.io.shortly.domain.shorturl.ShortUrl;
import com.io.shortly.infrastructure.shorturl.ShortUrlCacheImpl;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

class ShortUrlCacheImplTest {

    private CacheManager l1CacheManager;
    private CacheManager l2CacheManager;
    private ShortUrlCacheImpl cache;

    @BeforeEach
    void setUp() {
        l1CacheManager = new ConcurrentMapCacheManager("shortUrls");
        l2CacheManager = new ConcurrentMapCacheManager("shortUrls");
        cache = new ShortUrlCacheImpl(l1CacheManager, l2CacheManager);
    }

    @Test
    @DisplayName("L1 캐시에 값이 존재하면 L1 캐시에서 값을 반환한다")
    void getReturnsValueFromL1CacheWhenPresent() {
        ShortUrl shortUrl = ShortUrl.restore(1L, "code-1", "https://l1");
        l1CacheManager.getCache("shortUrls").put("code-1", shortUrl);

        Optional<ShortUrl> result = cache.get("code-1");

        assertThat(result).contains(shortUrl);
    }

    @Test
    @DisplayName("L2 캐시에만 값이 존재하면 L1 캐시로 승격시킨다")
    void getPromotesL2EntryIntoL1Cache() {
        ShortUrl shortUrl = ShortUrl.restore(2L, "code-2", "https://l2");
        l2CacheManager.getCache("shortUrls").put("code-2", shortUrl);

        Optional<ShortUrl> result = cache.get("code-2");

        assertThat(result).contains(shortUrl);
        Cache l1Cache = l1CacheManager.getCache("shortUrls");
        assertThat(l1Cache.get("code-2", ShortUrl.class)).isSameAs(shortUrl);
    }

    @Test
    @DisplayName("put 호출 시 L1, L2 캐시 모두에 값을 저장한다")
    void putStoresValueIntoBothCaches() {
        ShortUrl shortUrl = ShortUrl.restore(3L, "code-3", "https://store");

        cache.put(shortUrl);

        Cache l1Cache = l1CacheManager.getCache("shortUrls");
        Cache l2Cache = l2CacheManager.getCache("shortUrls");
        assertThat(l1Cache.get("code-3", ShortUrl.class)).isSameAs(shortUrl);
        assertThat(l2Cache.get("code-3", ShortUrl.class)).isSameAs(shortUrl);
    }

    @Test
    @DisplayName("캐시 미스 시 빈 Optional을 반환한다")
    void getReturnsEmptyWhenCachesMiss() {
        Optional<ShortUrl> result = cache.get("missing");

        assertThat(result).isEmpty();
    }
}

