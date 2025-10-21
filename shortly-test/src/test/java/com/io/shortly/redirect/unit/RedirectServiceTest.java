package com.io.shortly.redirect.unit;

import com.io.shortly.redirect.application.RedirectService;
import com.io.shortly.redirect.application.dto.RedirectResult;
import com.io.shortly.redirect.domain.Redirect;
import com.io.shortly.redirect.domain.RedirectCache;
import com.io.shortly.redirect.domain.RedirectEventPublisher;
import com.io.shortly.redirect.domain.RedirectRepository;
import com.io.shortly.redirect.domain.ShortCodeNotFoundException;
import com.io.shortly.shared.event.UrlClickedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Redirect Service 단위 테스트")
class RedirectServiceTest {

    private RedirectService redirectService;
    private MockRedirectCache mockCache;
    private MockRedirectRepository mockRepository;
    private MockRedirectEventPublisher mockEventPublisher;

    @BeforeEach
    void setUp() {
        mockCache = new MockRedirectCache();
        mockRepository = new MockRedirectRepository();
        mockEventPublisher = new MockRedirectEventPublisher();

        redirectService = new RedirectService(
            mockCache,
            mockRepository,
            mockEventPublisher
        );
    }

    @Test
    @DisplayName("캐시 히트 시 원본 URL을 반환한다")
    void getOriginalUrl_CacheHit_ReturnsOriginalUrl() {
        // Given
        String shortCode = "abc123";
        Redirect cachedRedirect = Redirect.create(shortCode, "https://example.com");
        mockCache.put(shortCode, cachedRedirect);

        // When & Then
        StepVerifier.create(redirectService.getOriginalUrl(shortCode))
            .assertNext(result -> {
                assertThat(result.originalUrl()).isEqualTo("https://example.com");
                assertThat(mockCache.getCount).isEqualTo(1);
                assertThat(mockRepository.findCount).isEqualTo(0); // DB not queried
            })
            .verifyComplete();
    }

    @Test
    @DisplayName("캐시 미스 시 DB 조회 후 캐시에 저장한다")
    void getOriginalUrl_CacheMiss_QueriesDbAndWarmsCache() {
        // Given
        String shortCode = "xyz789";
        Redirect dbRedirect = Redirect.create(shortCode, "https://database.com");
        mockRepository.addRedirect(shortCode, dbRedirect);

        // When & Then
        StepVerifier.create(redirectService.getOriginalUrl(shortCode))
            .assertNext(result -> {
                assertThat(result.originalUrl()).isEqualTo("https://database.com");
                assertThat(mockRepository.findCount).isEqualTo(1);
                assertThat(mockCache.putCount).isEqualTo(1); // Cache warmed
            })
            .verifyComplete();
    }

    @Test
    @DisplayName("존재하지 않는 단축 코드는 예외를 발생시킨다")
    void getOriginalUrl_NotFound_ThrowsException() {
        // Given
        String nonExistentCode = "notfound";

        // When & Then
        StepVerifier.create(redirectService.getOriginalUrl(nonExistentCode))
            .expectErrorMatches(error ->
                error instanceof ShortCodeNotFoundException &&
                error.getMessage().contains("Short code not found: notfound")
            )
            .verify();
    }

    @Test
    @DisplayName("빈 단축 코드는 예외를 발생시킨다")
    void getOriginalUrl_EmptyShortCode_ThrowsException() {
        // When & Then
        StepVerifier.create(redirectService.getOriginalUrl(""))
            .expectError(IllegalArgumentException.class)
            .verify();
    }

    @Test
    @DisplayName("단축 코드 형식이 잘못되면 예외를 발생시킨다")
    void getOriginalUrl_InvalidFormat_ThrowsException() {
        // Given - short code with invalid characters
        String invalidCode = "abc@#$";

        // When & Then
        StepVerifier.create(redirectService.getOriginalUrl(invalidCode))
            .expectErrorMatches(error ->
                error instanceof IllegalArgumentException &&
                error.getMessage().contains("6-10 alphanumeric characters")
            )
            .verify();
    }

    @Test
    @DisplayName("리다이렉트 성공 시 클릭 이벤트를 발행한다")
    void getOriginalUrl_Success_PublishesClickEvent() {
        // Given
        String shortCode = "evt123";
        Redirect redirect = Redirect.create(shortCode, "https://event-test.com");
        mockCache.put(shortCode, redirect);

        // When
        StepVerifier.create(redirectService.getOriginalUrl(shortCode))
            .assertNext(result -> {
                assertThat(result.originalUrl()).isEqualTo("https://event-test.com");
            })
            .verifyComplete();

        // Then
        assertThat(mockEventPublisher.publishedEvents).hasSize(1);
        UrlClickedEvent event = mockEventPublisher.publishedEvents.get(0);
        assertThat(event.getShortCode()).isEqualTo("evt123");
        assertThat(event.getOriginalUrl()).isEqualTo("https://event-test.com");
    }

    @Test
    @DisplayName("단축 코드 길이가 6자 미만이면 예외를 발생시킨다")
    void getOriginalUrl_TooShortCode_ThrowsException() {
        // Given
        String tooShortCode = "abc12"; // 5 characters

        // When & Then
        StepVerifier.create(redirectService.getOriginalUrl(tooShortCode))
            .expectError(IllegalArgumentException.class)
            .verify();
    }

    @Test
    @DisplayName("단축 코드 길이가 10자 초과면 예외를 발생시킨다")
    void getOriginalUrl_TooLongCode_ThrowsException() {
        // Given
        String tooLongCode = "abc12345678"; // 11 characters

        // When & Then
        StepVerifier.create(redirectService.getOriginalUrl(tooLongCode))
            .expectError(IllegalArgumentException.class)
            .verify();
    }

    // ========== Mock Implementations ==========

    /**
     * Mock RedirectCache - 캐시 동작 시뮬레이션
     */
    private static class MockRedirectCache implements RedirectCache {
        private final java.util.Map<String, Redirect> cache = new java.util.HashMap<>();
        int getCount = 0;
        int putCount = 0;
        int evictCount = 0;

        void put(String shortCode, Redirect redirect) {
            cache.put(shortCode, redirect);
        }

        @Override
        public Mono<Void> put(Redirect redirect) {
            putCount++;
            cache.put(redirect.getShortCode(), redirect);
            return Mono.empty();
        }

        @Override
        public Mono<Redirect> get(String shortCode) {
            getCount++;
            Redirect redirect = cache.get(shortCode);
            return redirect != null ? Mono.just(redirect) : Mono.empty();
        }

        @Override
        public Mono<Void> evict(String shortCode) {
            evictCount++;
            cache.remove(shortCode);
            return Mono.empty();
        }
    }

    /**
     * Mock RedirectRepository - DB 조회 시뮬레이션
     */
    private static class MockRedirectRepository implements RedirectRepository {
        private final java.util.Map<String, Redirect> storage = new java.util.HashMap<>();
        int findCount = 0;
        int saveCount = 0;

        void addRedirect(String shortCode, Redirect redirect) {
            storage.put(shortCode, redirect);
        }

        @Override
        public Mono<Redirect> findByShortCode(String shortCode) {
            findCount++;
            Redirect redirect = storage.get(shortCode);
            return redirect != null ? Mono.just(redirect) : Mono.empty();
        }

        @Override
        public Mono<Redirect> save(Redirect redirect) {
            saveCount++;
            storage.put(redirect.getShortCode(), redirect);
            return Mono.just(redirect);
        }

        @Override
        public Mono<Boolean> existsByShortCode(String shortCode) {
            return Mono.just(storage.containsKey(shortCode));
        }
    }

    /**
     * Mock RedirectEventPublisher - 이벤트 발행 기록
     */
    private static class MockRedirectEventPublisher implements RedirectEventPublisher {
        final List<UrlClickedEvent> publishedEvents = new ArrayList<>();

        @Override
        public void publishUrlClicked(UrlClickedEvent event) {
            publishedEvents.add(event);
        }
    }
}
