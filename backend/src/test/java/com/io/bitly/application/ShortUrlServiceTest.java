package com.io.bitly.application;

import com.io.bitly.application.dto.ShortenUrlCommand.CreateShortUrlCommand;
import com.io.bitly.application.dto.ShortenUrlCommand.ShortUrlLookupCommand;
import com.io.bitly.application.dto.ShortenUrlResult.CreateShortUrlResult;
import com.io.bitly.application.dto.ShortenUrlResult.ShortUrlLookupResult;
import com.io.bitly.domain.click.UrlClick;
import com.io.bitly.domain.click.UrlClickRepository;
import com.io.bitly.domain.shorturl.ShortUrl;
import com.io.bitly.domain.shorturl.ShortUrlGenerator;
import com.io.bitly.domain.shorturl.ShortUrlRepository;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ShortUrlServiceTest {

    @Test
    void shortenUrl_returnsCode_whenGeneratorProducesUniqueValue() {
        SequenceShortUrlGenerator generator = new SequenceShortUrlGenerator("abc123");
        InMemoryShortUrlRepository shortUrlRepository = new InMemoryShortUrlRepository();
        RecordingUrlClickRepository clickRepository = new RecordingUrlClickRepository();
        ShortUrlService service = new ShortUrlService(shortUrlRepository, clickRepository, generator);

        CreateShortUrlResult result = service.shortenUrl(CreateShortUrlCommand.of("https://example.com"));

        assertAll(
                () -> assertEquals("abc123", result.shortCode()),
                () -> assertEquals("https://example.com", result.originalUrl()),
                () -> assertEquals(1, shortUrlRepository.savedShortUrls.size()),
                () -> assertEquals("abc123", shortUrlRepository.savedShortUrls.get(0).getShortUrl())
        );
    }

    @Test
    void shortenUrl_retriesUntilUniqueCode() {
        SequenceShortUrlGenerator generator = new SequenceShortUrlGenerator("dup", "dup", "unique");
        InMemoryShortUrlRepository shortUrlRepository = new InMemoryShortUrlRepository();
        shortUrlRepository.presetExisting("dup");
        RecordingUrlClickRepository clickRepository = new RecordingUrlClickRepository();
        ShortUrlService service = new ShortUrlService(shortUrlRepository, clickRepository, generator);

        CreateShortUrlResult result = service.shortenUrl(CreateShortUrlCommand.of("https://retry.com"));

        assertAll(
                () -> assertEquals("unique", result.shortCode()),
                () -> assertEquals(3, generator.callCount),
                () -> assertEquals(1, shortUrlRepository.savedShortUrls.size()),
                () -> assertEquals("unique", shortUrlRepository.savedShortUrls.get(0).getShortUrl())
        );
    }

    @Test
    void shortenUrl_withSameOriginalProducesDifferentCodes() {
        SequenceShortUrlGenerator generator = new SequenceShortUrlGenerator("code1", "code2");
        InMemoryShortUrlRepository shortUrlRepository = new InMemoryShortUrlRepository();
        RecordingUrlClickRepository clickRepository = new RecordingUrlClickRepository();
        ShortUrlService service = new ShortUrlService(shortUrlRepository, clickRepository, generator);

        CreateShortUrlResult first = service.shortenUrl(CreateShortUrlCommand.of("https://dup.com"));
        CreateShortUrlResult second = service.shortenUrl(CreateShortUrlCommand.of("https://dup.com"));

        assertAll(
                () -> assertEquals("code1", first.shortCode()),
                () -> assertEquals("code2", second.shortCode()),
                () -> assertEquals(2, shortUrlRepository.savedShortUrls.size()),
                () -> assertEquals("code1", shortUrlRepository.savedShortUrls.get(0).getShortUrl()),
                () -> assertEquals("code2", shortUrlRepository.savedShortUrls.get(1).getShortUrl())
        );
    }

    @Test
    void shortenUrl_throwsWhenExceededAttemptLimit() {
        SequenceShortUrlGenerator generator = new SequenceShortUrlGenerator("dup", "dup", "dup", "dup", "dup");
        InMemoryShortUrlRepository shortUrlRepository = new InMemoryShortUrlRepository();
        shortUrlRepository.presetExisting("dup");
        RecordingUrlClickRepository clickRepository = new RecordingUrlClickRepository();
        ShortUrlService service = new ShortUrlService(shortUrlRepository, clickRepository, generator);

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> service.shortenUrl(CreateShortUrlCommand.of("https://overflow.com")));

        assertTrue(exception.getMessage().contains("Failed to generate unique short code"));
        assertEquals(5, generator.callCount);
        assertTrue(shortUrlRepository.savedShortUrls.isEmpty());
    }

    @Test
    void findOriginalUrl_returnsOriginalAndRecordsClick() {
        InMemoryShortUrlRepository shortUrlRepository = new InMemoryShortUrlRepository();
        ShortUrl persisted = shortUrlRepository.save(ShortUrl.of("abc123", "https://example.com"));
        RecordingUrlClickRepository clickRepository = new RecordingUrlClickRepository();
        ShortUrlService service = new ShortUrlService(shortUrlRepository, clickRepository, new SequenceShortUrlGenerator());

        ShortUrlLookupResult result = service.findOriginalUrl(ShortUrlLookupCommand.of("abc123"));

        assertAll(
                () -> assertEquals(persisted.getId(), result.urlId()),
                () -> assertEquals("https://example.com", result.originalUrl()),
                () -> assertEquals("abc123", result.shortCode()),
                () -> assertNotNull(clickRepository.lastSaved),
                () -> assertEquals(persisted.getId(), clickRepository.lastSaved.getUrlId())
        );
    }

    @Test
    void findOriginalUrl_throwsWhenShortCodeMissing() {
        InMemoryShortUrlRepository shortUrlRepository = new InMemoryShortUrlRepository();
        RecordingUrlClickRepository clickRepository = new RecordingUrlClickRepository();
        ShortUrlService service = new ShortUrlService(shortUrlRepository, clickRepository, new SequenceShortUrlGenerator());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.findOriginalUrl(ShortUrlLookupCommand.of("missing")));

        assertTrue(exception.getMessage().contains("Short code not found"));
        assertTrue(clickRepository.savedClicks.isEmpty());
    }

    private static final class SequenceShortUrlGenerator implements ShortUrlGenerator {

        private final Deque<String> values;
        private int callCount;

        SequenceShortUrlGenerator(String... values) {
            this.values = new ArrayDeque<>(List.of(values));
        }

        @Override
        public String generate(String seed) {
            callCount++;
            if (values.isEmpty()) {
                throw new IllegalStateException("No more generated values configured");
            }
            return values.removeFirst();
        }
    }

    private static final class InMemoryShortUrlRepository implements ShortUrlRepository {

        private final Map<String, ShortUrl> storage = new HashMap<>();
        private final Set<String> presetExisting = new HashSet<>();
        private long sequence = 1L;
        private final List<ShortUrl> savedShortUrls = new ArrayList<>();

        void presetExisting(String shortCode) {
            presetExisting.add(shortCode);
        }

        @Override
        public ShortUrl save(ShortUrl shortUrl) {
            ShortUrl persisted = ShortUrl.restore(sequence++, shortUrl.getShortUrl(), shortUrl.getOriginalUrl());
            storage.put(persisted.getShortUrl(), persisted);
            savedShortUrls.add(persisted);
            return persisted;
        }

        @Override
        public Optional<ShortUrl> findByShortUrl(String shortCode) {
            return Optional.ofNullable(storage.get(shortCode));
        }

        @Override
        public boolean existsByShortUrl(String shortCode) {
            return presetExisting.contains(shortCode) || storage.containsKey(shortCode);
        }
    }

    private static final class RecordingUrlClickRepository implements UrlClickRepository {

        private final List<UrlClick> savedClicks = new ArrayList<>();
        private UrlClick lastSaved;

        @Override
        public UrlClick save(UrlClick urlClick) {
            savedClicks.add(urlClick);
            lastSaved = urlClick;
            return urlClick;
        }
    }
}
