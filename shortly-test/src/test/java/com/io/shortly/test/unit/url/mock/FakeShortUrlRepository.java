package com.io.shortly.test.unit.url.mock;

import com.io.shortly.url.domain.ShortUrl;
import com.io.shortly.url.domain.ShortUrlRepository;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * ShortUrlRepository의 테스트용 Fake 구현체
 * 메모리 기반 저장소로 동작하며, 호출 검증을 위한 메서드 제공
 */
public class FakeShortUrlRepository implements ShortUrlRepository {

    private final Map<String, ShortUrl> storage = new HashMap<>();
    private int saveCallCount = 0;
    private ShortUrl lastSavedUrl = null;

    @Override
    public void save(ShortUrl shortUrl) {
        saveCallCount++;
        lastSavedUrl = shortUrl;
        storage.put(shortUrl.getShortCode(), shortUrl);
    }

    @Override
    public Optional<ShortUrl> findByShortCode(String shortCode) {
        return Optional.ofNullable(storage.get(shortCode));
    }

    // 테스트 검증용 메서드
    public int getSaveCallCount() {
        return saveCallCount;
    }

    public ShortUrl getLastSavedUrl() {
        return lastSavedUrl;
    }

    public void clear() {
        storage.clear();
        saveCallCount = 0;
        lastSavedUrl = null;
    }
}
