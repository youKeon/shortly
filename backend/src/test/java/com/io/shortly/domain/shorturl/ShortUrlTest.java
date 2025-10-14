package com.io.shortly.domain.shorturl;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ShortUrlTest {

    @Test
    @DisplayName("of() 팩토리 메서드로 새로운 ShortUrl을 생성한다")
    void of_createsNewShortUrl() {
        // given
        String shortUrl = "abc123";
        String originalUrl = "https://example.com";

        // when
        ShortUrl result = ShortUrl.of(shortUrl, originalUrl);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isNull();
        assertThat(result.getShortUrl()).isEqualTo(shortUrl);
        assertThat(result.getOriginalUrl()).isEqualTo(originalUrl);
        assertThat(result.getCreatedAt()).isNotNull();
        assertThat(result.getCreatedAt()).isBeforeOrEqualTo(LocalDateTime.now());
    }

    @Test
    @DisplayName("restore() 팩토리 메서드로 기존 ShortUrl을 복원한다")
    void restore_restoresExistingShortUrl() {
        // given
        Long id = 1L;
        String shortUrl = "xyz789";
        String originalUrl = "https://restored.com";

        // when
        ShortUrl result = ShortUrl.restore(id, shortUrl, originalUrl);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(id);
        assertThat(result.getShortUrl()).isEqualTo(shortUrl);
        assertThat(result.getOriginalUrl()).isEqualTo(originalUrl);
        assertThat(result.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("of()로 생성한 ShortUrl의 ID는 null이다")
    void of_hasNullId() {
        // when
        ShortUrl result = ShortUrl.of("code", "https://url.com");

        // then
        assertThat(result.getId()).isNull();
    }

    @Test
    @DisplayName("restore()로 복원한 ShortUrl은 지정된 ID를 갖는다")
    void restore_hasGivenId() {
        // given
        Long expectedId = 999L;

        // when
        ShortUrl result = ShortUrl.restore(expectedId, "code", "https://url.com");

        // then
        assertThat(result.getId()).isEqualTo(expectedId);
    }

    @Test
    @DisplayName("생성된 ShortUrl의 모든 필드가 올바르게 설정된다")
    void shortUrl_hasAllFieldsSet() {
        // given
        String shortUrl = "test12";
        String originalUrl = "https://test.example.com/very/long/path";

        // when
        ShortUrl result = ShortUrl.of(shortUrl, originalUrl);

        // then
        assertThat(result.getShortUrl()).isEqualTo(shortUrl);
        assertThat(result.getOriginalUrl()).isEqualTo(originalUrl);
        assertThat(result.getCreatedAt()).isBetween(
                LocalDateTime.now().minusSeconds(1),
                LocalDateTime.now().plusSeconds(1)
        );
    }
}

