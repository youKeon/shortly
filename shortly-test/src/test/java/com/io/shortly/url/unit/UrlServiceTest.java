package com.io.shortly.url.unit;

import com.io.shortly.url.application.UrlService;
import com.io.shortly.url.application.dto.ShortUrlCommand.ShortenCommand;
import com.io.shortly.url.application.dto.ShortUrlResult.ShortenedResult;
import com.io.shortly.url.domain.ShortUrl;
import com.io.shortly.url.domain.ShortUrlGenerator;
import com.io.shortly.url.domain.ShortUrlRepository;
import com.io.shortly.url.domain.event.UrlCreatedDomainEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.*;

@DisplayName("URL Service 단위 테스트")
class UrlServiceTest {

    private UrlService urlService;
    private MockShortUrlRepository mockRepository;
    private MockShortUrlGenerator mockGenerator;
    private MockApplicationEventPublisher mockEventPublisher;
    private MockTransactionTemplate mockTransactionTemplate;

    @BeforeEach
    void setUp() {
        mockRepository = new MockShortUrlRepository();
        mockGenerator = new MockShortUrlGenerator();
        mockEventPublisher = new MockApplicationEventPublisher();
        mockTransactionTemplate = new MockTransactionTemplate();

        urlService = new UrlService(
            mockRepository,
            mockGenerator,
            mockEventPublisher,
            mockTransactionTemplate
        );
    }

    @Test
    @DisplayName("유효한 URL을 단축 코드로 변환한다")
    void shortenUrl_ValidUrl_ReturnsShortCode() {
        // Given
        ShortenCommand command = ShortenCommand.of("https://example.com");
        mockGenerator.setGeneratedCode("abc123");

        // When
        ShortenedResult result = urlService.shortenUrl(command);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.shortCode()).isEqualTo("abc123");
        assertThat(result.originalUrl()).isEqualTo("https://example.com");
        assertThat(mockRepository.saveCount).isEqualTo(1);
        assertThat(mockEventPublisher.publishedEvents).hasSize(1);
    }

    @Test
    @DisplayName("중복 코드 발생 시 재시도 후 성공한다")
    void shortenUrl_DuplicateCode_RetriesAndSucceeds() {
        // Given
        ShortenCommand command = ShortenCommand.of("https://example.com");
        mockGenerator.setSequence("dup001", "dup002", "success");
        mockRepository.setDuplicateOnAttempts(1, 2); // 1st, 2nd attempt fail

        // When
        ShortenedResult result = urlService.shortenUrl(command);

        // Then
        assertThat(result.shortCode()).isEqualTo("success");
        assertThat(mockRepository.saveCount).isEqualTo(3); // 2 failures + 1 success
    }

    @Test
    @DisplayName("최대 재시도 횟수 초과 시 예외를 발생시킨다")
    void shortenUrl_MaxAttemptsExceeded_ThrowsException() {
        // Given
        ShortenCommand command = ShortenCommand.of("https://example.com");
        mockRepository.setAlwaysDuplicate(true); // All attempts fail

        // When & Then
        assertThatThrownBy(() -> urlService.shortenUrl(command))
            .isInstanceOf(com.io.shortly.url.domain.ShortCodeGenerationFailedException.class)
            .hasMessageContaining("Failed to generate unique short code after 5 attempts");
    }

    @Test
    @DisplayName("null 커맨드는 예외를 발생시킨다")
    void shortenUrl_NullCommand_ThrowsException() {
        // When & Then
        assertThatThrownBy(() -> urlService.shortenUrl(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Command must not be null");
    }

    @Test
    @DisplayName("빈 URL은 예외를 발생시킨다")
    void shortenUrl_EmptyUrl_ThrowsException() {
        // Given
        ShortenCommand command = ShortenCommand.of("");

        // When & Then
        assertThatThrownBy(() -> urlService.shortenUrl(command))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("URL must not be blank");
    }

    @Test
    @DisplayName("URL 저장 성공 시 도메인 이벤트를 발행한다")
    void shortenUrl_SaveSuccess_PublishesDomainEvent() {
        // Given
        ShortenCommand command = ShortenCommand.of("https://example.com");
        mockGenerator.setGeneratedCode("evt123");

        // When
        urlService.shortenUrl(command);

        // Then
        assertThat(mockEventPublisher.publishedEvents).hasSize(1);
        Object event = mockEventPublisher.publishedEvents.get(0);
        assertThat(event).isInstanceOf(UrlCreatedDomainEvent.class);

        UrlCreatedDomainEvent domainEvent = (UrlCreatedDomainEvent) event;
        assertThat(domainEvent.shortCode()).isEqualTo("evt123");
        assertThat(domainEvent.originalUrl()).isEqualTo("https://example.com");
    }

    @Test
    @DisplayName("중복이 아닌 DB 제약 위반은 예외를 발생시킨다")
    void shortenUrl_NonDuplicateConstraintViolation_ThrowsException() {
        // Given
        ShortenCommand command = ShortenCommand.of("https://example.com");
        mockRepository.setNonDuplicateConstraintViolation(true);

        // When & Then
        assertThatThrownBy(() -> urlService.shortenUrl(command))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Database constraint violation");
    }

    // ========== Mock Implementations ==========

    /**
     * Mock ShortUrlRepository - 저장 동작과 중복 검사를 시뮬레이션
     */
    private static class MockShortUrlRepository implements ShortUrlRepository {
        private final java.util.Map<String, ShortUrl> storage = new java.util.HashMap<>();
        int saveCount = 0;
        private int[] duplicateAttempts = new int[0];
        private boolean alwaysDuplicate = false;
        private boolean nonDuplicateConstraintViolation = false;

        void setDuplicateOnAttempts(int... attempts) {
            this.duplicateAttempts = attempts;
        }

        void setAlwaysDuplicate(boolean alwaysDuplicate) {
            this.alwaysDuplicate = alwaysDuplicate;
        }

        void setNonDuplicateConstraintViolation(boolean enable) {
            this.nonDuplicateConstraintViolation = enable;
        }

        @Override
        public ShortUrl save(ShortUrl shortUrl) {
            saveCount++;

            // Non-duplicate constraint violation
            if (nonDuplicateConstraintViolation) {
                throw new DataIntegrityViolationException(
                    "Check constraint violation",
                    new SQLException("CHECK constraint failed")
                );
            }

            // Check if this attempt should fail with duplicate
            if (alwaysDuplicate || isDuplicateAttempt(saveCount)) {
                throw new DataIntegrityViolationException(
                    "Duplicate entry violation",
                    new SQLException("Duplicate entry '" + shortUrl.getShortCode() + "' for key 'UK_short_code'")
                );
            }

            storage.put(shortUrl.getShortCode(), shortUrl);
            return shortUrl;
        }

        @Override
        public java.util.Optional<ShortUrl> findByShortCode(String shortCode) {
            return java.util.Optional.ofNullable(storage.get(shortCode));
        }

        @Override
        public boolean existsByShortCode(String shortCode) {
            return storage.containsKey(shortCode);
        }

        private boolean isDuplicateAttempt(int attemptNumber) {
            for (int duplicateAttempt : duplicateAttempts) {
                if (duplicateAttempt == attemptNumber) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * Mock ShortUrlGenerator - 단축 코드 생성을 시뮬레이션
     */
    private static class MockShortUrlGenerator implements ShortUrlGenerator {
        private String[] codeSequence;
        private int currentIndex = 0;

        void setGeneratedCode(String code) {
            this.codeSequence = new String[]{code};
        }

        void setSequence(String... codes) {
            this.codeSequence = codes;
            this.currentIndex = 0;
        }

        @Override
        public String generate(String originalUrl) {
            if (codeSequence == null || codeSequence.length == 0) {
                return "default";
            }
            String code = codeSequence[currentIndex];
            if (currentIndex < codeSequence.length - 1) {
                currentIndex++;
            }
            return code;
        }
    }

    /**
     * Mock ApplicationEventPublisher - 이벤트 발행 기록
     */
    private static class MockApplicationEventPublisher implements ApplicationEventPublisher {
        java.util.List<Object> publishedEvents = new java.util.ArrayList<>();

        @Override
        public void publishEvent(Object event) {
            publishedEvents.add(event);
        }
    }

    /**
     * Mock TransactionTemplate - 트랜잭션 실행을 시뮬레이션
     */
    private static class MockTransactionTemplate extends TransactionTemplate {
        public MockTransactionTemplate() {
            super(null); // No actual transaction manager needed for unit tests
        }

        @Override
        public <T> T execute(org.springframework.transaction.support.TransactionCallback<T> action) {
            // Execute without actual transaction (unit test mode)
            return action.doInTransaction(null);
        }
    }
}
