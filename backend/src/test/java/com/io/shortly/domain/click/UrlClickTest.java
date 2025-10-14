package com.io.shortly.domain.click;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UrlClickTest {

    @Test
    @DisplayName("of() 팩토리 메서드로 새로운 UrlClick을 생성한다")
    void of_createsNewUrlClick() {
        // given
        Long urlId = 100L;

        // when
        UrlClick result = UrlClick.of(urlId);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isNull();
        assertThat(result.getUrlId()).isEqualTo(urlId);
        assertThat(result.getCreatedAt()).isNotNull();
        assertThat(result.getCreatedAt()).isBeforeOrEqualTo(LocalDateTime.now());
    }

    @Test
    @DisplayName("restore() 팩토리 메서드로 기존 UrlClick을 복원한다")
    void restore_restoresExistingUrlClick() {
        // given
        Long id = 1L;
        Long urlId = 200L;
        LocalDateTime createdAt = LocalDateTime.now().minusDays(1);

        // when
        UrlClick result = UrlClick.restore(id, urlId, createdAt);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(id);
        assertThat(result.getUrlId()).isEqualTo(urlId);
        assertThat(result.getCreatedAt()).isEqualTo(createdAt);
    }

    @Test
    @DisplayName("of()로 생성한 UrlClick의 ID는 null이다")
    void of_hasNullId() {
        // when
        UrlClick result = UrlClick.of(123L);

        // then
        assertThat(result.getId()).isNull();
    }

    @Test
    @DisplayName("restore()로 복원한 UrlClick은 지정된 ID를 갖는다")
    void restore_hasGivenId() {
        // given
        Long expectedId = 999L;
        LocalDateTime now = LocalDateTime.now();

        // when
        UrlClick result = UrlClick.restore(expectedId, 456L, now);

        // then
        assertThat(result.getId()).isEqualTo(expectedId);
    }

    @Test
    @DisplayName("생성된 UrlClick의 모든 필드가 올바르게 설정된다")
    void urlClick_hasAllFieldsSet() {
        // given
        Long urlId = 789L;

        // when
        UrlClick result = UrlClick.of(urlId);

        // then
        assertThat(result.getUrlId()).isEqualTo(urlId);
        assertThat(result.getCreatedAt()).isBetween(
                LocalDateTime.now().minusSeconds(1),
                LocalDateTime.now().plusSeconds(1)
        );
    }

    @Test
    @DisplayName("restore()로 복원 시 제공된 createdAt을 그대로 사용한다")
    void restore_usesProvidedCreatedAt() {
        // given
        LocalDateTime expectedCreatedAt = LocalDateTime.of(2024, 1, 1, 12, 0, 0);

        // when
        UrlClick result = UrlClick.restore(1L, 100L, expectedCreatedAt);

        // then
        assertThat(result.getCreatedAt()).isEqualTo(expectedCreatedAt);
    }
}

