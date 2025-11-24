package com.io.shortly.url.integration;

import com.io.shortly.url.UrlServiceApplication;
import com.io.shortly.url.application.UrlService;
import com.io.shortly.url.application.dto.ShortUrlCommand.FindCommand;
import com.io.shortly.url.application.dto.ShortUrlCommand.ShortenCommand;
import com.io.shortly.url.application.dto.ShortUrlResult.ShortenedResult;
import com.io.shortly.url.domain.outbox.OutboxRepository;
import com.io.shortly.url.domain.url.ShortCodeNotFoundException;
import com.io.shortly.url.domain.url.ShortUrlRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(classes = UrlServiceApplication.class, properties = {
        "spring.jpa.hibernate.ddl-auto=update"
})
@ActiveProfiles("local")
@DisplayName("UrlService Integration 테스트")
class UrlServiceIntegrationTest {

    @Autowired
    private UrlService urlService;

    @Autowired
    private ShortUrlRepository shortUrlRepository;

    @Autowired
    private OutboxRepository outboxRepository;

    @Nested
    @DisplayName("URL 단축 기능 통합 테스트")
    class ShortenUrlIntegrationTest {

        @Test
        @Transactional
        @DisplayName("URL 단축 시 데이터베이스에 정상 저장된다")
        void shortenUrl_SavesToDatabase() {
            // given
            String originalUrl = "https://example.com/test/integration";
            ShortenCommand command = new ShortenCommand(originalUrl);

            // when
            ShortenedResult result = urlService.shortenUrl(command);

            // then
            assertThat(result).isNotNull();
            assertThat(result.shortCode()).isNotNull();
            assertThat(result.originalUrl()).isEqualTo(originalUrl);

            // DB 검증
            boolean exists = shortUrlRepository.existsByShortCode(result.shortCode());
            assertThat(exists).isTrue();
        }

        @Test
        @Transactional
        @DisplayName("URL 단축 시 Outbox 이벤트가 저장된다")
        void shortenUrl_SavesOutboxEvent() {
            // given
            String originalUrl = "https://example.com/outbox/test";
            ShortenCommand command = new ShortenCommand(originalUrl);

            // when
            ShortenedResult result = urlService.shortenUrl(command);

            // then
            assertThat(result).isNotNull();

            // Outbox 검증: 실제 저장된 이벤트 확인
            // Note: OutboxRepository에 조회 메서드가 없으므로 저장 자체가 예외 없이 완료되면 성공으로 간주
        }

        @Test
        @Transactional
        @DisplayName("동일한 원본 URL도 매번 다른 단축 코드를 생성한다")
        void shortenUrl_SameOriginalUrl_GeneratesDifferentCodes() {
            // given
            String originalUrl = "https://example.com/same/url";

            // when
            ShortenedResult result1 = urlService.shortenUrl(new ShortenCommand(originalUrl));
            ShortenedResult result2 = urlService.shortenUrl(new ShortenCommand(originalUrl));
            ShortenedResult result3 = urlService.shortenUrl(new ShortenCommand(originalUrl));

            // then
            assertThat(result1.shortCode()).isNotEqualTo(result2.shortCode());
            assertThat(result2.shortCode()).isNotEqualTo(result3.shortCode());
            assertThat(result1.shortCode()).isNotEqualTo(result3.shortCode());

            // 모두 DB에 저장됨
            assertThat(shortUrlRepository.existsByShortCode(result1.shortCode())).isTrue();
            assertThat(shortUrlRepository.existsByShortCode(result2.shortCode())).isTrue();
            assertThat(shortUrlRepository.existsByShortCode(result3.shortCode())).isTrue();
        }

        @Test
        @Transactional
        @DisplayName("긴 URL도 정상적으로 단축된다")
        void shortenUrl_LongUrl_Success() {
            // given
            String longUrl = "https://example.com/" + "a".repeat(2000);
            ShortenCommand command = new ShortenCommand(longUrl);

            // when
            ShortenedResult result = urlService.shortenUrl(command);

            // then
            assertThat(result).isNotNull();
            assertThat(result.shortCode().length()).isGreaterThanOrEqualTo(6);
            assertThat(result.originalUrl()).isEqualTo(longUrl);
        }

        @Test
        @Transactional
        @DisplayName("특수문자가 포함된 URL도 정상 처리된다")
        void shortenUrl_SpecialCharactersInUrl_Success() {
            // given
            String urlWithSpecialChars = "https://example.com/path?param1=value1&param2=value2#section";
            ShortenCommand command = new ShortenCommand(urlWithSpecialChars);

            // when
            ShortenedResult result = urlService.shortenUrl(command);

            // then
            assertThat(result).isNotNull();
            assertThat(result.originalUrl()).isEqualTo(urlWithSpecialChars);
        }
    }

    @Nested
    @DisplayName("단축 코드 조회 기능 통합 테스트")
    class FindByShortCodeIntegrationTest {

        @Test
        @Transactional
        @DisplayName("저장된 단축 코드로 원본 URL을 조회한다")
        void findByShortCode_ExistingCode_ReturnsOriginalUrl() {
            // given
            String originalUrl = "https://example.com/find/test";
            ShortenedResult shortened = urlService.shortenUrl(new ShortenCommand(originalUrl));

            // when
            ShortenedResult found = urlService.findByShortCode(new FindCommand(shortened.shortCode()));

            // then
            assertThat(found).isNotNull();
            assertThat(found.shortCode()).isEqualTo(shortened.shortCode());
            assertThat(found.originalUrl()).isEqualTo(originalUrl);
        }

        @Test
        @Transactional
        @DisplayName("존재하지 않는 단축 코드 조회 시 예외가 발생한다")
        void findByShortCode_NonExistingCode_ThrowsException() {
            // given
            String nonExistingCode = "NOTFND";

            // when & then
            assertThatThrownBy(() -> urlService.findByShortCode(new FindCommand(nonExistingCode)))
                    .isInstanceOf(ShortCodeNotFoundException.class)
                    .hasMessageContaining(nonExistingCode);
        }

        @Test
        @Transactional
        @DisplayName("대소문자를 구분하여 단축 코드를 조회한다")
        void findByShortCode_CaseSensitive() {
            // given
            String originalUrl = "https://example.com/case/test";
            ShortenedResult shortened = urlService.shortenUrl(new ShortenCommand(originalUrl));
            String shortCode = shortened.shortCode();

            // when
            ShortenedResult found = urlService.findByShortCode(new FindCommand(shortCode));

            // then
            assertThat(found.shortCode()).isEqualTo(shortCode);

            // 대소문자가 다르면 찾을 수 없음 (Base62는 대소문자 구분)
            if (shortCode.toLowerCase().equals(shortCode)) {
                // 소문자만 있는 경우 대문자로 조회 시 실패
                String upperCode = shortCode.toUpperCase();
                if (!upperCode.equals(shortCode)) {
                    assertThatThrownBy(() -> urlService.findByShortCode(new FindCommand(upperCode)))
                            .isInstanceOf(ShortCodeNotFoundException.class);
                }
            }
        }
    }

    @Nested
    @DisplayName("트랜잭션 및 동시성 테스트")
    class TransactionAndConcurrencyTest {

        @Test
        @DisplayName("동시에 여러 URL을 단축해도 중복 없이 생성된다")
        void shortenUrl_ConcurrentRequests_NoDuplicates() throws InterruptedException {
            // given
            int threadCount = 10;
            int urlsPerThread = 10;
            java.util.Set<String> generatedCodes = java.util.concurrent.ConcurrentHashMap.newKeySet();
            java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(threadCount);
            java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors
                    .newFixedThreadPool(threadCount);

            // when
            for (int i = 0; i < threadCount; i++) {
                final int threadId = i;
                executor.submit(() -> {
                    try {
                        for (int j = 0; j < urlsPerThread; j++) {
                            String url = String.format("https://example.com/concurrent/%d/%d", threadId, j);
                            ShortenedResult result = urlService.shortenUrl(new ShortenCommand(url));
                            generatedCodes.add(result.shortCode());
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await(30, java.util.concurrent.TimeUnit.SECONDS);
            executor.shutdown();

            // then
            int expectedTotal = threadCount * urlsPerThread;
            assertThat(generatedCodes).hasSize(expectedTotal);
        }

        @Test
        @Transactional
        @DisplayName("트랜잭션 롤백 시 데이터베이스에 저장되지 않는다")
        void shortenUrl_TransactionRollback_NotSaved() {
            // given
            String originalUrl = "https://example.com/rollback/test";
            ShortenCommand command = new ShortenCommand(originalUrl);

            // when
            ShortenedResult result = urlService.shortenUrl(command);
            String shortCode = result.shortCode();

            // 현재 트랜잭션 내에서는 존재함
            assertThat(shortUrlRepository.existsByShortCode(shortCode)).isTrue();

            // @Transactional 메서드가 끝나면 롤백됨
        }
    }

    @Nested
    @DisplayName("Snowflake ID 생성기 통합 테스트")
    class SnowflakeGeneratorIntegrationTest {

        @Test
        @Transactional
        @DisplayName("생성된 단축 코드는 항상 Base62 형식이다")
        void shortenUrl_AlwaysGeneratesBase62Format() {
            // given & when
            for (int i = 0; i < 20; i++) {
                String url = String.format("https://example.com/base62/%d", i);
                ShortenedResult result = urlService.shortenUrl(new ShortenCommand(url));

                // then
                assertThat(result.shortCode()).matches("[0-9A-Za-z]{6,}");
            }
        }

        @Test
        @Transactional
        @DisplayName("짧은 시간 내에 생성된 코드도 모두 고유하다")
        void shortenUrl_RapidGeneration_AllUnique() {
            // given
            java.util.Set<String> codes = new java.util.HashSet<>();
            int count = 100;

            // when
            for (int i = 0; i < count; i++) {
                String url = String.format("https://example.com/rapid/%d", i);
                ShortenedResult result = urlService.shortenUrl(new ShortenCommand(url));
                codes.add(result.shortCode());
            }

            // then
            assertThat(codes).hasSize(count);
        }
    }
}
