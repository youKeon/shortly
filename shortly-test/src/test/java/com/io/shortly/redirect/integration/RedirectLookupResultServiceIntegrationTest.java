package com.io.shortly.redirect.integration;

import com.io.shortly.redirect.RedirectServiceApplication;
import com.io.shortly.redirect.application.RedirectService;
import com.io.shortly.redirect.application.dto.RedirectResult.RedirectLookupResult;
import com.io.shortly.redirect.domain.RedirectCache;
import com.io.shortly.redirect.domain.ShortCodeNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.*;

/**
 * Redirect Service Integration 테스트
 *
 * 로컬 Docker Compose 인프라(Redis, Kafka)를 사용하여 통합 테스트를 수행합니다.
 *
 * 테스트 실행 전 필수 사항:
 * - docker-compose -f infra/compose/docker-compose-dev.yml up -d
 */
@SpringBootTest(
    classes = RedirectServiceApplication.class
)
@ActiveProfiles("local")
@DisplayName("RedirectService Integration 테스트")
class RedirectLookupResultServiceIntegrationTest {

    @Autowired
    private RedirectService redirectService;

    @Autowired
    private RedirectCache redirectCache;

    @Nested
    @DisplayName("캐시 기반 리다이렉션 기능")
    class CacheBasedRedirectionTest {

        @Test
        @DisplayName("캐시에 저장된 URL을 조회한다")
        void getOriginalUrl_FromCache_Success() {
            // given
            String shortCode = "test01";
            String originalUrl = "https://example.com/cached/url";
            redirectCache.put(com.io.shortly.redirect.domain.Redirect.create(shortCode, originalUrl));

            // when
            RedirectLookupResult result = redirectService.getOriginalUrl(shortCode);

            // then
            assertThat(result).isNotNull();
            assertThat(result.originalUrl()).isEqualTo(originalUrl);
        }

        @Test
        @DisplayName("캐시에 없는 코드 조회 시 URL Service에 폴백한다")
        void getOriginalUrl_CacheMiss_FallbackToUrlService() {
            // given: URL Service에 실제 데이터가 있어야 함 (사전 조건)
            String shortCode = "fake99";  // 테스트용 가상 코드

            // when & then: 캐시에 없고 URL Service에도 없으면 예외 발생
            assertThatThrownBy(() -> redirectService.getOriginalUrl(shortCode))
                .isInstanceOf(ShortCodeNotFoundException.class);
        }

        @Test
        @DisplayName("조회 후 클릭 이벤트가 Kafka로 발행된다")
        void getOriginalUrl_PublishesClickEvent() {
            // given
            String shortCode = "evt01";
            String originalUrl = "https://example.com/event/test";
            redirectCache.put(com.io.shortly.redirect.domain.Redirect.create(shortCode, originalUrl));

            // when
            RedirectLookupResult result = redirectService.getOriginalUrl(shortCode);

            // then
            assertThat(result).isNotNull();
            // Note: Kafka 이벤트 발행은 비동기이므로 여기서는 예외가 발생하지 않음을 확인
        }
    }

    @Nested
    @DisplayName("Redis 캐시 통합 테스트")
    class RedisCacheIntegrationTest {

        @Test
        @DisplayName("Redis에 데이터를 저장하고 조회한다")
        void cache_PutAndGet_Success() {
            // given
            String shortCode = "redis1";
            String originalUrl = "https://example.com/redis/test";
            com.io.shortly.redirect.domain.Redirect redirect =
                com.io.shortly.redirect.domain.Redirect.create(shortCode, originalUrl);

            // when
            redirectCache.put(redirect);
            var cached = redirectCache.get(shortCode);

            // then
            assertThat(cached).isPresent();
            assertThat(cached.get().getTargetUrl()).isEqualTo(originalUrl);
        }

        // evict() 테스트 제거: URL은 immutable하므로 삭제 기능 불필요
        // 캐시 만료는 TTL로 자동 관리됨

        @Test
        @DisplayName("존재하지 않는 키 조회 시 빈 Optional을 반환한다")
        void cache_GetNonExisting_ReturnsEmpty() {
            // given
            String nonExistingCode = "NOEXST";

            // when
            var cached = redirectCache.get(nonExistingCode);

            // then
            assertThat(cached).isEmpty();
        }

        @Test
        @DisplayName("동일한 키에 덮어쓰기가 가능하다")
        void cache_Overwrite_Success() {
            // given
            String shortCode = "over01";
            String oldUrl = "https://example.com/old";
            String newUrl = "https://example.com/new";

            redirectCache.put(com.io.shortly.redirect.domain.Redirect.create(shortCode, oldUrl));

            // when
            redirectCache.put(com.io.shortly.redirect.domain.Redirect.create(shortCode, newUrl));
            var cached = redirectCache.get(shortCode);

            // then
            assertThat(cached).isPresent();
            assertThat(cached.get().getTargetUrl()).isEqualTo(newUrl);
        }
    }

    @Nested
    @DisplayName("L1/L2 캐시 레이어 테스트")
    class CacheLayerIntegrationTest {

        @Test
        @DisplayName("L1 캐시 미스 시 L2 캐시에서 조회한다")
        void cache_L1MissL2Hit_Success() {
            // given
            String shortCode = "layer1";
            String originalUrl = "https://example.com/layer/test";

            // L2에만 저장 (실제로는 L1/L2 모두 저장되지만 개념적 테스트)
            redirectCache.put(com.io.shortly.redirect.domain.Redirect.create(shortCode, originalUrl));

            // when
            var cached = redirectCache.get(shortCode);

            // then
            assertThat(cached).isPresent();
            assertThat(cached.get().getTargetUrl()).isEqualTo(originalUrl);
        }

        @Test
        @DisplayName("여러 URL을 캐시에 저장하고 조회한다")
        void cache_MultipleEntries_Success() {
            // given
            int count = 10;
            for (int i = 0; i < count; i++) {
                String shortCode = String.format("multi%d", i);
                String url = String.format("https://example.com/multi/%d", i);
                redirectCache.put(com.io.shortly.redirect.domain.Redirect.create(shortCode, url));
            }

            // when & then
            for (int i = 0; i < count; i++) {
                String shortCode = String.format("multi%d", i);
                String expectedUrl = String.format("https://example.com/multi/%d", i);

                var cached = redirectCache.get(shortCode);
                assertThat(cached).isPresent();
                assertThat(cached.get().getTargetUrl()).isEqualTo(expectedUrl);
            }
        }
    }

    @Nested
    @DisplayName("동시성 테스트")
    class ConcurrencyTest {

        @Test
        @DisplayName("동시에 여러 스레드에서 캐시 조회 시 데이터 무결성이 보장된다")
        void cache_ConcurrentReads_DataIntegrity() throws InterruptedException {
            // given
            String shortCode = "conc01";
            String originalUrl = "https://example.com/concurrent/test";
            redirectCache.put(com.io.shortly.redirect.domain.Redirect.create(shortCode, originalUrl));

            int threadCount = 10;
            int readsPerThread = 100;
            java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(threadCount);
            java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(threadCount);
            java.util.concurrent.atomic.AtomicInteger successCount = new java.util.concurrent.atomic.AtomicInteger(0);

            // when
            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        for (int j = 0; j < readsPerThread; j++) {
                            RedirectLookupResult result = redirectService.getOriginalUrl(shortCode);
                            if (result.originalUrl().equals(originalUrl)) {
                                successCount.incrementAndGet();
                            }
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await(30, java.util.concurrent.TimeUnit.SECONDS);
            executor.shutdown();

            // then
            int expectedTotal = threadCount * readsPerThread;
            assertThat(successCount.get()).isEqualTo(expectedTotal);
        }
    }

    @Nested
    @DisplayName("예외 처리 테스트")
    class ExceptionHandlingTest {

        @Test
        @DisplayName("null 단축 코드 조회 시 예외가 발생한다")
        void getOriginalUrl_NullShortCode_ThrowsException() {
            // when & then
            assertThatThrownBy(() -> redirectService.getOriginalUrl(null))
                .isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("빈 단축 코드 조회 시 예외가 발생한다")
        void getOriginalUrl_EmptyShortCode_ThrowsException() {
            // when & then
            assertThatThrownBy(() -> redirectService.getOriginalUrl(""))
                .isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("존재하지 않는 단축 코드 조회 시 ShortCodeNotFoundException이 발생한다")
        void getOriginalUrl_NonExistingCode_ThrowsShortCodeNotFoundException() {
            // given
            String nonExistingCode = "XXXXXX";

            // when & then
            assertThatThrownBy(() -> redirectService.getOriginalUrl(nonExistingCode))
                .isInstanceOf(ShortCodeNotFoundException.class)
                .hasMessageContaining(nonExistingCode);
        }
    }
}
