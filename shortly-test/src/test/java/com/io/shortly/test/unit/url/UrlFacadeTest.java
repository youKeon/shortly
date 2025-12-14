package com.io.shortly.test.unit.url;

import static org.junit.jupiter.api.Assertions.*;

import com.io.shortly.shared.event.UrlCreatedEvent;
import com.io.shortly.test.unit.url.mock.FakeShortUrlEventPublisher;
import com.io.shortly.test.unit.url.mock.FakeShortUrlGenerator;
import com.io.shortly.test.unit.url.mock.FakeShortUrlRepository;
import com.io.shortly.url.application.UrlFacade;
import com.io.shortly.url.application.dto.ShortUrlCommand.FindCommand;
import com.io.shortly.url.application.dto.ShortUrlCommand.ShortenCommand;
import com.io.shortly.url.application.dto.ShortUrlResult.ShortenedResult;
import com.io.shortly.url.domain.GeneratedShortCode;
import com.io.shortly.url.domain.ShortCodeNotFoundException;
import com.io.shortly.url.domain.ShortUrl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("UrlFacade 비즈니스 로직 단위 테스트")
class UrlFacadeTest {

    private UrlFacade urlFacade;
    private FakeShortUrlRepository repository;
    private FakeShortUrlGenerator generator;
    private FakeShortUrlEventPublisher eventPublisher;

    @BeforeEach
    void setUp() {
        repository = new FakeShortUrlRepository();
        generator = new FakeShortUrlGenerator();
        eventPublisher = new FakeShortUrlEventPublisher();
        urlFacade = new UrlFacade(repository, generator, eventPublisher);
    }

    @Test
    @DisplayName("URL 단축 생성 - 정상 케이스")
    void shortenUrl_Success() {
        // given
        String originalUrl = "https://example.com/very/long/url";
        GeneratedShortCode generatedCode = GeneratedShortCode.of(999L, "abc123");
        generator.setResultToReturn(generatedCode);

        ShortenCommand command = new ShortenCommand(originalUrl);

        // when
        ShortenedResult result = urlFacade.shortenUrl(command);

        // then
        assertNotNull(result);
        assertEquals("abc123", result.shortCode());
        assertEquals(originalUrl, result.originalUrl());
    }

    @Test
    @DisplayName("URL 단축 생성 - Generator 호출 검증")
    void shortenUrl_CallsGenerator() {
        // given
        String originalUrl = "https://example.com";
        ShortenCommand command = new ShortenCommand(originalUrl);

        // when
        urlFacade.shortenUrl(command);

        // then
        assertEquals(1, generator.getGenerateCallCount());
        assertEquals(originalUrl, generator.getLastSeed());
    }

    @Test
    @DisplayName("URL 단축 생성 - Repository 저장 검증")
    void shortenUrl_SavesToRepository() {
        // given
        String originalUrl = "https://example.com";
        GeneratedShortCode generatedCode = GeneratedShortCode.of(777L, "xyz789");
        generator.setResultToReturn(generatedCode);

        ShortenCommand command = new ShortenCommand(originalUrl);

        // when
        urlFacade.shortenUrl(command);

        // then
        assertEquals(1, repository.getSaveCallCount());

        ShortUrl savedUrl = repository.getLastSavedUrl();
        assertNotNull(savedUrl);
        assertEquals("xyz789", savedUrl.getShortCode());
        assertEquals(originalUrl, savedUrl.getOriginalUrl());
    }

    @Test
    @DisplayName("URL 단축 생성 - 이벤트 발행 검증")
    void shortenUrl_PublishesEvent() {
        // given
        String originalUrl = "https://example.com";
        GeneratedShortCode generatedCode = GeneratedShortCode.of(12345L, "test99");
        generator.setResultToReturn(generatedCode);

        ShortenCommand command = new ShortenCommand(originalUrl);

        // when
        urlFacade.shortenUrl(command);

        // then
        assertEquals(1, eventPublisher.getPublishCallCount());

        UrlCreatedEvent event = eventPublisher.getLastPublishedEvent();
        assertNotNull(event);
        assertEquals(12345L, event.getEventId());
        assertEquals("test99", event.getShortCode());
        assertEquals(originalUrl, event.getOriginalUrl());
    }

    @Test
    @DisplayName("URL 단축 생성 - null command 예외")
    void shortenUrl_NullCommand_ThrowsException() {
        // when & then
        assertThrows(IllegalArgumentException.class, () -> {
            urlFacade.shortenUrl(null);
        });
    }

    @Test
    @DisplayName("URL 단축 생성 - 빈 URL 예외")
    void shortenUrl_BlankUrl_ThrowsException() {
        // given
        ShortenCommand command = new ShortenCommand("");

        // when & then
        assertThrows(IllegalArgumentException.class, () -> {
            urlFacade.shortenUrl(command);
        });
    }

    @Test
    @DisplayName("Short Code로 URL 조회 - 정상 케이스")
    void findByShortCode_Success() {
        // given
        String shortCode = "abc123";
        String originalUrl = "https://example.com";
        ShortUrl shortUrl = ShortUrl.create(shortCode, originalUrl);
        repository.save(shortUrl);

        FindCommand command = new FindCommand(shortCode);

        // when
        ShortenedResult result = urlFacade.findByShortCode(command);

        // then
        assertNotNull(result);
        assertEquals(shortCode, result.shortCode());
        assertEquals(originalUrl, result.originalUrl());
    }

    @Test
    @DisplayName("Short Code로 URL 조회 - 존재하지 않는 코드 예외")
    void findByShortCode_NotFound_ThrowsException() {
        // given
        FindCommand command = new FindCommand("notfound");

        // when & then
        ShortCodeNotFoundException exception = assertThrows(
            ShortCodeNotFoundException.class,
            () -> urlFacade.findByShortCode(command)
        );

        assertTrue(exception.getMessage().contains("notfound"));
    }

    @Test
    @DisplayName("Short Code로 URL 조회 - null command 예외")
    void findByShortCode_NullCommand_ThrowsException() {
        // when & then
        assertThrows(IllegalArgumentException.class, () -> {
            urlFacade.findByShortCode(null);
        });
    }

    @Test
    @DisplayName("Short Code로 URL 조회 - 빈 shortCode 예외")
    void findByShortCode_BlankShortCode_ThrowsException() {
        // given
        FindCommand command = new FindCommand("");

        // when & then
        assertThrows(IllegalArgumentException.class, () -> {
            urlFacade.findByShortCode(command);
        });
    }

    @Test
    @DisplayName("여러 URL 단축 생성 - 순차 처리 검증")
    void shortenUrl_MultipleUrls_ProcessedSequentially() {
        // given
        String url1 = "https://example.com/1";
        String url2 = "https://example.com/2";
        String url3 = "https://example.com/3";

        // when
        urlFacade.shortenUrl(new ShortenCommand(url1));
        urlFacade.shortenUrl(new ShortenCommand(url2));
        urlFacade.shortenUrl(new ShortenCommand(url3));

        // then
        assertEquals(3, generator.getGenerateCallCount());
        assertEquals(3, repository.getSaveCallCount());
        assertEquals(3, eventPublisher.getPublishCallCount());
        assertEquals(3, eventPublisher.getPublishedEvents().size());
    }
}
