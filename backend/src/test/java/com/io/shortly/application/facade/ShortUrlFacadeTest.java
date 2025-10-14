package com.io.shortly.application.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.io.shortly.application.dto.ShortenUrlCommand.CreateShortUrlCommand;
import com.io.shortly.application.dto.ShortenUrlCommand.ShortUrlLookupCommand;
import com.io.shortly.application.dto.ShortenUrlResult.CreateShortUrlResult;
import com.io.shortly.application.dto.ShortenUrlResult.ShortUrlLookupResult;
import com.io.shortly.domain.click.ClickService;
import com.io.shortly.domain.shorturl.ShortUrl;
import com.io.shortly.domain.shorturl.ShortUrlCache;
import com.io.shortly.domain.shorturl.ShortUrlGenerator;
import com.io.shortly.domain.shorturl.ShortUrlRepository;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ShortUrlFacadeTest {

    private InMemoryShortUrlRepository repository;
    private RecordingShortUrlCache cache;
    private StubShortUrlGenerator generator;
    private RecordingClickService clickService;
    private ShortUrlFacade facade;

    @BeforeEach
    void setUp() {
        repository = new InMemoryShortUrlRepository();
        cache = new RecordingShortUrlCache();
        generator = new StubShortUrlGenerator();
        clickService = new RecordingClickService();
        facade = new ShortUrlFacade(repository, generator, cache, clickService);
    }

    @Test
    @DisplayName("URL 단축 시 고유한 코드를 생성하고 캐시에 저장한다")
    void shortenUrl_generatesUniqueCodeAndCachesResult() {
        generator.enqueue("dup-1", "fresh-1");
        repository.save(ShortUrl.of("dup-1", "http://existing"));

        CreateShortUrlResult result = facade.shortenUrl(CreateShortUrlCommand.of("https://example.com"));

        assertThat(result.shortCode()).isEqualTo("fresh-1");
        assertThat(cache.get("fresh-1")).isPresent();
        assertThat(repository.existsByShortUrl("fresh-1")).isTrue();
        assertThat(generator.getGeneratedSequence()).containsExactly("dup-1", "fresh-1");
    }

    @Test
    @DisplayName("고유한 코드를 생성할 수 없으면 예외를 발생시킨다")
    void shortenUrl_throwsWhenGeneratorCannotProduceUniqueCode() {
        generator.enqueue("dup-1");
        repository.save(ShortUrl.of("dup-1", "http://existing"));

        assertThrows(IllegalStateException.class, () ->
            facade.shortenUrl(CreateShortUrlCommand.of("https://example.com"))
        );
    }

    @Test
    @DisplayName("캐시에 값이 있으면 Repository 조회 없이 캐시에서 반환한다")
    void findOriginalUrl_returnsCachedValueWithoutRepositoryLookup() {
        ShortUrl cached = repository.save(ShortUrl.of("code-1", "https://cached"));
        cache.put(cached);
        repository.resetFindByShortUrlCallCount();

        ShortUrlLookupResult result = facade.findOriginalUrl(ShortUrlLookupCommand.of("code-1"));

        assertThat(result.originalUrl()).isEqualTo("https://cached");
        assertThat(clickService.incrementedIds).containsExactly(cached.getId());
        assertThat(repository.getFindByShortUrlCallCount()).isZero();
    }

    @Test
    @DisplayName("캐시 미스 시 Repository에서 조회하고 캐시에 저장한다")
    void findOriginalUrl_loadsFromRepositoryAndCachesWhenMissing() {
        ShortUrl stored = repository.save(ShortUrl.of("miss-1", "https://missing"));
        cache.clear();

        ShortUrlLookupResult result = facade.findOriginalUrl(ShortUrlLookupCommand.of("miss-1"));

        assertThat(result.originalUrl()).isEqualTo("https://missing");
        assertThat(clickService.incrementedIds).containsExactly(stored.getId());
        assertThat(cache.get("miss-1")).isPresent();
        assertThat(repository.getFindByShortUrlCallCount()).isEqualTo(1);
    }

    private static class InMemoryShortUrlRepository implements ShortUrlRepository {

        private final Map<String, ShortUrl> storage = new HashMap<>();
        private final AtomicLong idSequence = new AtomicLong(1);
        private int findByShortUrlCallCount;

        @Override
        public ShortUrl save(ShortUrl shortUrl) {
            long id = idSequence.getAndIncrement();
            ShortUrl persisted = ShortUrl.restore(id, shortUrl.getShortUrl(), shortUrl.getOriginalUrl());
            storage.put(persisted.getShortUrl(), persisted);
            return persisted;
        }

        @Override
        public Optional<ShortUrl> findByShortUrl(String shortCode) {
            findByShortUrlCallCount++;
            return Optional.ofNullable(storage.get(shortCode));
        }

        @Override
        public boolean existsByShortUrl(String shortCode) {
            return storage.containsKey(shortCode);
        }

        void resetFindByShortUrlCallCount() {
            findByShortUrlCallCount = 0;
        }

        int getFindByShortUrlCallCount() {
            return findByShortUrlCallCount;
        }
    }

    private static class StubShortUrlGenerator implements ShortUrlGenerator {

        private final Deque<String> queue = new ArrayDeque<>();
        private final List<String> generatedSequence = new ArrayList<>();

        void enqueue(String... codes) {
            for (String code : codes) {
                queue.addLast(code);
            }
        }

        @Override
        public String generate(String seed) {
            if (queue.isEmpty()) {
                throw new IllegalStateException("No codes configured for generator");
            }
            String code = queue.removeFirst();
            generatedSequence.add(code);
            return code;
        }

        List<String> getGeneratedSequence() {
            return generatedSequence;
        }
    }

    private static class RecordingShortUrlCache implements ShortUrlCache {

        private final Map<String, ShortUrl> values = new HashMap<>();

        @Override
        public Optional<ShortUrl> get(String shortCode) {
            return Optional.ofNullable(values.get(shortCode));
        }

        @Override
        public void put(ShortUrl shortUrl) {
            values.put(shortUrl.getShortUrl(), shortUrl);
        }

        void clear() {
            values.clear();
        }
    }

    private static class RecordingClickService implements ClickService {

        private final List<Long> incrementedIds = new ArrayList<>();

        @Override
        public void incrementClickCount(Long urlId) {
            incrementedIds.add(urlId);
        }
    }
}
