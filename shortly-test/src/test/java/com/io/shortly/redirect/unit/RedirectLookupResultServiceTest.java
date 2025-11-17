package com.io.shortly.redirect.unit;

import com.io.shortly.redirect.application.RedirectService;
import com.io.shortly.redirect.application.dto.RedirectResult.RedirectLookupResult;
import com.io.shortly.redirect.domain.DistributedLockTemplate;
import com.io.shortly.redirect.domain.Redirect;
import com.io.shortly.redirect.domain.RedirectCache;
import com.io.shortly.redirect.domain.RedirectEventPublisher;
import com.io.shortly.redirect.domain.ShortCodeNotFoundException;
import com.io.shortly.redirect.infrastructure.restclient.UrlLookupResponse;
import com.io.shortly.shared.event.UrlClickedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@DisplayName("RedirectService 단위 테스트")
class RedirectLookupResultServiceTest {

    @Nested
    @DisplayName("리다이렉션 조회 기능")
    class GetOriginalUrlTest {

        @Test
        @DisplayName("캐시 히트 시 원본 URL을 반환하고 클릭 이벤트를 발행한다")
        void getOriginalUrl_CacheHit() {
            // given
            String shortCode = "abc123";
            String targetUrl = "https://example.com";

            StubRedirectCache cache = new StubRedirectCache();
            cache.addRedirect(shortCode, targetUrl);

            RestClient mockRestClient = mock(RestClient.class);
            StubDistributedLockTemplate lockTemplate = new StubDistributedLockTemplate();
            SpyRedirectEventPublisher eventPublisher = new SpyRedirectEventPublisher();

            RedirectService service = new RedirectService(mockRestClient, cache, lockTemplate, eventPublisher);

            // when
            RedirectLookupResult result = service.getOriginalUrl(shortCode);

            // then
            assertThat(result).isNotNull();
            assertThat(result.originalUrl()).isEqualTo(targetUrl);
            assertThat(eventPublisher.getPublishedEvents()).hasSize(1);
            assertThat(eventPublisher.getPublishedEvents().get(0).getShortCode()).isEqualTo(shortCode);
            assertThat(lockTemplate.isLockAcquired()).isFalse(); // 캐시 히트 시 락 획득 안 함
            verifyNoInteractions(mockRestClient); // 캐시 히트 시 RestClient 호출 안 함
        }

        @Test
        @DisplayName("캐시 미스 시 URL Service를 호출하고 캐시에 저장한다")
        void getOriginalUrl_CacheMiss() {
            // given
            String shortCode = "notcached";
            String targetUrl = "https://example.com/new";

            StubRedirectCache cache = new StubRedirectCache();

            // RestClient Mock 설정
            RestClient mockRestClient = createMockRestClient(shortCode, targetUrl);

            StubDistributedLockTemplate lockTemplate = new StubDistributedLockTemplate();
            SpyRedirectEventPublisher eventPublisher = new SpyRedirectEventPublisher();

            RedirectService service = new RedirectService(mockRestClient, cache, lockTemplate, eventPublisher);

            // when
            RedirectLookupResult result = service.getOriginalUrl(shortCode);

            // then
            assertThat(result).isNotNull();
            assertThat(result.originalUrl()).isEqualTo(targetUrl);
            assertThat(eventPublisher.getPublishedEvents()).hasSize(1);
            assertThat(lockTemplate.isLockAcquired()).isTrue(); // 캐시 미스 시 락 획득
            assertThat(cache.get(shortCode)).isPresent(); // 캐시에 저장됨
        }

        @Test
        @DisplayName("URL Service에서 찾지 못하면 예외를 발생시킨다")
        void getOriginalUrl_NotFound() {
            // given
            String shortCode = "notfound";

            StubRedirectCache cache = new StubRedirectCache();

            // RestClient Mock 설정 - 404 응답
            RestClient mockRestClient = createMockRestClientWithError();

            StubDistributedLockTemplate lockTemplate = new StubDistributedLockTemplate();
            SpyRedirectEventPublisher eventPublisher = new SpyRedirectEventPublisher();

            RedirectService service = new RedirectService(mockRestClient, cache, lockTemplate, eventPublisher);

            // when & then
            assertThatThrownBy(() -> service.getOriginalUrl(shortCode))
                .isInstanceOf(ShortCodeNotFoundException.class)
                .hasMessageContaining(shortCode);

            assertThat(eventPublisher.getPublishedEvents()).isEmpty(); // 이벤트 발행 안 함
        }
    }

    // ==================== Test Doubles ====================

    static class StubRedirectCache implements RedirectCache {
        private final List<Redirect> redirects = new ArrayList<>();

        @Override
        public Optional<Redirect> get(String shortCode) {
            return redirects.stream()
                .filter(r -> r.getShortCode().equals(shortCode))
                .findFirst();
        }

        @Override
        public void put(Redirect redirect) {
            redirects.add(redirect);
        }

        void addRedirect(String shortCode, String targetUrl) {
            redirects.add(Redirect.create(shortCode, targetUrl));
        }
    }

    static class StubDistributedLockTemplate implements DistributedLockTemplate {
        private boolean lockAcquired = false;

        @Override
        public <T> T executeWithLock(String lockKey, Supplier<T> task) {
            lockAcquired = true;
            return task.get();
        }

        boolean isLockAcquired() {
            return lockAcquired;
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

    // ==================== Helper Methods ====================

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static RestClient createMockRestClient(String shortCode, String targetUrl) {
        RestClient mockRestClient = mock(RestClient.class);
        RestClient.RequestHeadersUriSpec mockUriSpec = mock(RestClient.RequestHeadersUriSpec.class);
        RestClient.ResponseSpec mockResponseSpec = mock(RestClient.ResponseSpec.class);

        when(mockRestClient.get()).thenReturn(mockUriSpec);
        when(mockUriSpec.uri(anyString(), any(Object.class))).thenReturn(mockUriSpec);
        when(mockUriSpec.retrieve()).thenReturn(mockResponseSpec);
        when(mockResponseSpec.onStatus(any(), any())).thenReturn(mockResponseSpec);
        when(mockResponseSpec.body(UrlLookupResponse.class))
            .thenReturn(new UrlLookupResponse(shortCode, targetUrl));

        return mockRestClient;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static RestClient createMockRestClientWithError() {
        RestClient mockRestClient = mock(RestClient.class);
        RestClient.RequestHeadersUriSpec mockUriSpec = mock(RestClient.RequestHeadersUriSpec.class);
        RestClient.ResponseSpec mockResponseSpec = mock(RestClient.ResponseSpec.class);

        when(mockRestClient.get()).thenReturn(mockUriSpec);
        when(mockUriSpec.uri(anyString(), any(Object.class))).thenReturn(mockUriSpec);
        when(mockUriSpec.retrieve()).thenReturn(mockResponseSpec);
        when(mockResponseSpec.onStatus(any(), any())).thenReturn(mockResponseSpec);
        when(mockResponseSpec.body(UrlLookupResponse.class))
            .thenThrow(new RestClientException("404 Not Found"));

        return mockRestClient;
    }
}
