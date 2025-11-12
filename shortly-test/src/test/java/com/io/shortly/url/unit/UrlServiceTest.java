package com.io.shortly.url.unit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.io.shortly.url.application.UrlService;
import com.io.shortly.url.application.dto.ShortUrlCommand.ShortenCommand;
import com.io.shortly.url.application.dto.ShortUrlResult.ShortenedResult;
import com.io.shortly.url.domain.event.Outbox;
import com.io.shortly.url.domain.event.OutboxRepository;
import com.io.shortly.url.domain.url.ShortCodeGenerationFailedException;
import com.io.shortly.url.domain.url.ShortCodeNotFoundException;
import com.io.shortly.url.domain.url.ShortUrl;
import com.io.shortly.url.domain.url.ShortUrlGenerator;
import com.io.shortly.url.domain.url.ShortUrlRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

@DisplayName("UrlService 단위 테스트")
class UrlServiceTest {

    @Nested
    @DisplayName("URL 단축 기능")
    class ShortenUrlTest {

        private final String originalUrl = "https://example.com/long/url";
        private final String shortCode = "abc123";

        @Test
        @DisplayName("정상적으로 URL을 단축하고 Outbox 이벤트를 저장한다")
        void shortenUrl_Success() {
            // given
            StubShortUrlGenerator generator = new StubShortUrlGenerator(shortCode);
            FakeShortUrlRepository urlRepository = new FakeShortUrlRepository();
            FakeOutboxRepository outboxRepository = new FakeOutboxRepository();
            StubObjectMapper objectMapper = new StubObjectMapper();
            SuccessTransactionTemplate transactionTemplate = new SuccessTransactionTemplate();

            UrlService urlService = new UrlService(
                urlRepository,
                generator,
                outboxRepository,
                objectMapper,
                transactionTemplate
            );

            ShortenCommand command = new ShortenCommand(originalUrl);

            // when
            ShortenedResult result = urlService.shortenUrl(command);

            // then
            assertThat(result).isNotNull();
            assertThat(result.shortCode()).isEqualTo(shortCode);
            assertThat(result.originalUrl()).isEqualTo(originalUrl);
            assertThat(urlRepository.getSavedShortUrls()).hasSize(1);
            assertThat(outboxRepository.getSavedOutboxes()).hasSize(1);
        }

        @Test
        @DisplayName("단축 코드 중복 시 재시도한다")
        void shortenUrl_RetryOnDuplicateShortCode() {
            // given
            String firstCode = "abc123";
            String secondCode = "def456";
            MultipleCodeGenerator generator = new MultipleCodeGenerator(firstCode, secondCode);
            FakeShortUrlRepository urlRepository = new FakeShortUrlRepository();
            FakeOutboxRepository outboxRepository = new FakeOutboxRepository();
            StubObjectMapper objectMapper = new StubObjectMapper();
            RetryOnceTransactionTemplate transactionTemplate = new RetryOnceTransactionTemplate();

            UrlService urlService = new UrlService(
                urlRepository,
                generator,
                outboxRepository,
                objectMapper,
                transactionTemplate
            );

            ShortenCommand command = new ShortenCommand(originalUrl);

            // when
            ShortenedResult result = urlService.shortenUrl(command);

            // then
            assertThat(result).isNotNull();
            assertThat(result.shortCode()).isEqualTo(secondCode);
            assertThat(generator.getGenerateCallCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("최대 재시도 횟수 초과 시 예외를 발생시킨다")
        void shortenUrl_ThrowsExceptionAfterMaxAttempts() {
            // given
            MultipleCodeGenerator generator = new MultipleCodeGenerator("a1", "a2", "a3", "a4", "a5");
            FakeShortUrlRepository urlRepository = new FakeShortUrlRepository();
            FakeOutboxRepository outboxRepository = new FakeOutboxRepository();
            StubObjectMapper objectMapper = new StubObjectMapper();
            AlwaysFailTransactionTemplate transactionTemplate = new AlwaysFailTransactionTemplate();

            UrlService urlService = new UrlService(
                urlRepository,
                generator,
                outboxRepository,
                objectMapper,
                transactionTemplate
            );

            ShortenCommand command = new ShortenCommand(originalUrl);

            // when & then
            assertThatThrownBy(() -> urlService.shortenUrl(command))
                .isInstanceOf(ShortCodeGenerationFailedException.class)
                .hasMessageContaining("5");

            assertThat(generator.getGenerateCallCount()).isEqualTo(5);
        }

        @Test
        @DisplayName("중복이 아닌 DB 제약 조건 위반 시 예외를 발생시킨다")
        void shortenUrl_ThrowsExceptionOnOtherConstraintViolation() {
            // given
            StubShortUrlGenerator generator = new StubShortUrlGenerator(shortCode);
            FakeShortUrlRepository urlRepository = new FakeShortUrlRepository();
            FakeOutboxRepository outboxRepository = new FakeOutboxRepository();
            StubObjectMapper objectMapper = new StubObjectMapper();
            ForeignKeyConstraintTransactionTemplate transactionTemplate =
                new ForeignKeyConstraintTransactionTemplate();

            UrlService urlService = new UrlService(
                urlRepository,
                generator,
                outboxRepository,
                objectMapper,
                transactionTemplate
            );

            ShortenCommand command = new ShortenCommand(originalUrl);

            // when & then
            assertThatThrownBy(() -> urlService.shortenUrl(command))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Database constraint violation");
        }

        @Test
        @DisplayName("Outbox 이벤트 직렬화 실패 시 예외를 발생시킨다")
        void shortenUrl_ThrowsExceptionOnSerializationFailure() {
            // given
            StubShortUrlGenerator generator = new StubShortUrlGenerator(shortCode);
            FakeShortUrlRepository urlRepository = new FakeShortUrlRepository();
            FakeOutboxRepository outboxRepository = new FakeOutboxRepository();
            FailingObjectMapper objectMapper = new FailingObjectMapper();
            SuccessTransactionTemplate transactionTemplate = new SuccessTransactionTemplate();

            UrlService urlService = new UrlService(
                urlRepository,
                generator,
                outboxRepository,
                objectMapper,
                transactionTemplate
            );

            ShortenCommand command = new ShortenCommand(originalUrl);

            // when & then
            assertThatThrownBy(() -> urlService.shortenUrl(command))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to create outbox event");
        }
    }

    @Nested
    @DisplayName("단축 코드 조회 기능")
    class FindByShortCodeTest {

        @Test
        @DisplayName("존재하는 단축 코드로 원본 URL을 조회한다")
        void findByShortCode_Success() {
            // given
            String shortCode = "abc123";
            String originalUrl = "https://example.com/long/url";
            ShortUrl shortUrl = ShortUrl.create(shortCode, originalUrl);

            FakeShortUrlRepository urlRepository = new FakeShortUrlRepository();
            urlRepository.save(shortUrl);

            UrlService urlService = new UrlService(
                urlRepository,
                null,
                null,
                null,
                null
            );

            // when
            ShortenedResult result = urlService.findByShortCode(shortCode);

            // then
            assertThat(result).isNotNull();
            assertThat(result.shortCode()).isEqualTo(shortCode);
            assertThat(result.originalUrl()).isEqualTo(originalUrl);
        }

        @Test
        @DisplayName("존재하지 않는 단축 코드 조회 시 예외를 발생시킨다")
        void findByShortCode_ThrowsExceptionWhenNotFound() {
            // given
            String shortCode = "notfound";
            FakeShortUrlRepository urlRepository = new FakeShortUrlRepository();

            UrlService urlService = new UrlService(
                urlRepository,
                null,
                null,
                null,
                null
            );

            // when & then
            assertThatThrownBy(() -> urlService.findByShortCode(shortCode))
                .isInstanceOf(ShortCodeNotFoundException.class)
                .hasMessageContaining(shortCode);
        }
    }

    // ==================== Test Doubles ====================

    static class StubShortUrlGenerator implements ShortUrlGenerator {
        private final String shortCode;

        StubShortUrlGenerator(String shortCode) {
            this.shortCode = shortCode;
        }

        @Override
        public String generate(String seed) {
            return shortCode;
        }
    }

    static class MultipleCodeGenerator implements ShortUrlGenerator {
        private final String[] codes;
        private int callCount = 0;

        MultipleCodeGenerator(String... codes) {
            this.codes = codes;
        }

        @Override
        public String generate(String seed) {
            return codes[callCount++];
        }

        int getGenerateCallCount() {
            return callCount;
        }
    }

    static class FakeShortUrlRepository implements ShortUrlRepository {
        private final List<ShortUrl> storage = new ArrayList<>();

        @Override
        public void save(ShortUrl shortUrl) {
            storage.add(shortUrl);
        }

        @Override
        public Optional<ShortUrl> findByShortCode(String shortCode) {
            return storage.stream()
                .filter(url -> url.getShortCode().equals(shortCode))
                .findFirst();
        }

        @Override
        public boolean existsByShortCode(String shortCode) {
            return storage.stream()
                .anyMatch(url -> url.getShortCode().equals(shortCode));
        }

        List<ShortUrl> getSavedShortUrls() {
            return storage;
        }
    }

    static class FakeOutboxRepository implements OutboxRepository {
        private final List<Outbox> storage = new ArrayList<>();

        @Override
        public void save(Outbox outbox) {
            storage.add(outbox);
        }

        List<Outbox> getSavedOutboxes() {
            return storage;
        }
    }

    static class StubObjectMapper extends ObjectMapper {
        @Override
        public String writeValueAsString(Object value) {
            return "{\"event\":\"data\"}";
        }
    }

    static class FailingObjectMapper extends ObjectMapper {
        @Override
        public String writeValueAsString(Object value) throws JsonProcessingException {
            throw new JsonProcessingException("Serialization failed") {};
        }
    }

    static class SuccessTransactionTemplate extends TransactionTemplate {
        @Override
        public <T> T execute(TransactionCallback<T> action) {
            return action.doInTransaction(null);
        }
    }

    static class RetryOnceTransactionTemplate extends TransactionTemplate {
        private int callCount = 0;

        @Override
        public <T> T execute(TransactionCallback<T> action) {
            callCount++;
            if (callCount == 1) {
                throw new DataIntegrityViolationException("Duplicate entry 'abc123'");
            }
            return action.doInTransaction(null);
        }
    }

    static class AlwaysFailTransactionTemplate extends TransactionTemplate {
        @Override
        public <T> T execute(TransactionCallback<T> action) {
            throw new DataIntegrityViolationException("Duplicate entry");
        }
    }

    static class ForeignKeyConstraintTransactionTemplate extends TransactionTemplate {
        @Override
        public <T> T execute(TransactionCallback<T> action) {
            throw new DataIntegrityViolationException(
                "Foreign key constraint",
                new SQLException()
            );
        }
    }
}
