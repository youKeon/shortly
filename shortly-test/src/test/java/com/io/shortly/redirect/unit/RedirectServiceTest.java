package com.io.shortly.redirect.unit;

import com.io.shortly.redirect.application.RedirectService;
import com.io.shortly.redirect.application.dto.RedirectResult.Redirect;
import com.io.shortly.redirect.domain.RedirectCache;
import com.io.shortly.redirect.domain.RedirectEventPublisher;
import com.io.shortly.redirect.domain.ShortCodeNotFoundException;
import com.io.shortly.redirect.infrastructure.client.UrlServiceClient;
import com.io.shortly.shared.event.UrlClickedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

@DisplayName("RedirectService 단위 테스트")
class RedirectServiceTest {

    @Nested
    @DisplayName("리다이렉션 조회 기능")
    class GetOriginalUrlTest {

        @Test
        @DisplayName("캐시에 있는 경우 원본 URL을 반환하고 클릭 이벤트를 발행한다")
        void getOriginalUrl_FromCache() {
            // given
            String shortCode = "abc123";
            String targetUrl = "https://example.com";

            FakeRedirectCache cache = new FakeRedirectCache();
            cache.put(com.io.shortly.redirect.domain.Redirect.create(shortCode, targetUrl));

            StubUrlServiceClient urlServiceClient = new StubUrlServiceClient();
            SpyRedirectEventPublisher eventPublisher = new SpyRedirectEventPublisher();

            RedirectService service = new RedirectService(cache, urlServiceClient, eventPublisher);

            // when
            Redirect result = service.getOriginalUrl(shortCode);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getOriginalUrl()).isEqualTo(targetUrl);
            assertThat(eventPublisher.getPublishedEvents()).hasSize(1);
            assertThat(eventPublisher.getPublishedEvents().get(0).getShortCode()).isEqualTo(shortCode);
            assertThat(urlServiceClient.getCallCount()).isEqualTo(0); // API 호출 안 함
        }

        @Test
        @DisplayName("캐시 미스 시 URL Service API를 호출하고 캐시에 저장한다")
        void getOriginalUrl_CacheMiss_FallbackToAPI() {
            // given
            String shortCode = "abc123";
            String targetUrl = "https://example.com";

            FakeRedirectCache cache = new FakeRedirectCache();
            StubUrlServiceClient urlServiceClient = new StubUrlServiceClient();
            urlServiceClient.addResponse(shortCode, targetUrl);

            SpyRedirectEventPublisher eventPublisher = new SpyRedirectEventPublisher();

            RedirectService service = new RedirectService(cache, urlServiceClient, eventPublisher);

            // when
            Redirect result = service.getOriginalUrl(shortCode);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getOriginalUrl()).isEqualTo(targetUrl);
            assertThat(cache.get(shortCode)).isPresent(); // 캐시에 저장됨
            assertThat(eventPublisher.getPublishedEvents()).hasSize(1);
            assertThat(urlServiceClient.getCallCount()).isEqualTo(1); // API 1번 호출
        }

        @Test
        @DisplayName("캐시와 API 모두에서 찾지 못하면 예외를 발생시킨다")
        void getOriginalUrl_NotFound() {
            // given
            String shortCode = "notfound";

            FakeRedirectCache cache = new FakeRedirectCache();
            StubUrlServiceClient urlServiceClient = new StubUrlServiceClient();
            SpyRedirectEventPublisher eventPublisher = new SpyRedirectEventPublisher();

            RedirectService service = new RedirectService(cache, urlServiceClient, eventPublisher);

            // when & then
            assertThatThrownBy(() -> service.getOriginalUrl(shortCode))
                .isInstanceOf(ShortCodeNotFoundException.class)
                .hasMessageContaining(shortCode);

            assertThat(eventPublisher.getPublishedEvents()).isEmpty(); // 이벤트 발행 안 함
        }

        @Test
        @DisplayName("API 호출 실패 시 예외를 발생시킨다")
        void getOriginalUrl_APICallFails() {
            // given
            String shortCode = "abc123";

            FakeRedirectCache cache = new FakeRedirectCache();
            FailingUrlServiceClient urlServiceClient = new FailingUrlServiceClient();
            SpyRedirectEventPublisher eventPublisher = new SpyRedirectEventPublisher();

            RedirectService service = new RedirectService(cache, urlServiceClient, eventPublisher);

            // when & then
            assertThatThrownBy(() -> service.getOriginalUrl(shortCode))
                .isInstanceOf(ShortCodeNotFoundException.class);

            assertThat(eventPublisher.getPublishedEvents()).isEmpty();
        }

        @Test
        @DisplayName("캐시 워밍업 후 재조회 시 캐시에서 바로 반환한다")
        void getOriginalUrl_CacheWarmup() {
            // given
            String shortCode = "abc123";
            String targetUrl = "https://example.com";

            FakeRedirectCache cache = new FakeRedirectCache();
            StubUrlServiceClient urlServiceClient = new StubUrlServiceClient();
            urlServiceClient.addResponse(shortCode, targetUrl);

            SpyRedirectEventPublisher eventPublisher = new SpyRedirectEventPublisher();

            RedirectService service = new RedirectService(cache, urlServiceClient, eventPublisher);

            // when: 첫 번째 조회 (캐시 미스 → API 호출 → 캐시 저장)
            service.getOriginalUrl(shortCode);

            // then: 두 번째 조회 (캐시 히트 → API 호출 안 함)
            service.getOriginalUrl(shortCode);

            assertThat(urlServiceClient.getCallCount()).isEqualTo(1); // API 1번만 호출
            assertThat(eventPublisher.getPublishedEvents()).hasSize(2); // 이벤트 2번 발행
        }
    }

    // ==================== Test Doubles ====================

    static class FakeRedirectCache implements RedirectCache {
        private final Map<String, com.io.shortly.redirect.domain.Redirect> storage = new HashMap<>();

        @Override
        public Optional<com.io.shortly.redirect.domain.Redirect> get(String shortCode) {
            return Optional.ofNullable(storage.get(shortCode));
        }

        @Override
        public void put(com.io.shortly.redirect.domain.Redirect redirect) {
            storage.put(redirect.getShortCode(), redirect);
        }

        @Override
        public void evict(String shortCode) {
            storage.remove(shortCode);
        }
    }

    static class StubUrlServiceClient extends UrlServiceClient {
        private final Map<String, String> responses = new HashMap<>();
        private int callCount = 0;

        public StubUrlServiceClient() {
            super(null); // RestClient는 사용하지 않음
        }

        @Override
        public Optional<com.io.shortly.redirect.domain.Redirect> findByShortCode(String shortCode) {
            callCount++;
            String targetUrl = responses.get(shortCode);

            if (targetUrl == null) {
                return Optional.empty();
            }

            return Optional.of(com.io.shortly.redirect.domain.Redirect.create(shortCode, targetUrl));
        }

        void addResponse(String shortCode, String targetUrl) {
            responses.put(shortCode, targetUrl);
        }

        int getCallCount() {
            return callCount;
        }
    }

    static class FailingUrlServiceClient extends UrlServiceClient {
        public FailingUrlServiceClient() {
            super(null);
        }

        @Override
        public Optional<com.io.shortly.redirect.domain.Redirect> findByShortCode(String shortCode) {
            return Optional.empty();
        }
    }

    static class SpyRedirectEventPublisher implements RedirectEventPublisher {
        private final List<UrlClickedEvent> publishedEvents = new ArrayList<>();

        @Override
        public void publishUrlClicked(UrlClickedEvent event) {
            publishedEvents.add(event);
        }

        List<UrlClickedEvent> getPublishedEvents() {
            return publishedEvents;
        }
    }
}
