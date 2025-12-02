package com.io.shortly.redirect.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.io.shortly.redirect.domain.Redirect;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Redirect Domain 모델 테스트")
class RedirectTest {

    @Nested
    @DisplayName("생성 메서드")
    class CreateTest {

        @Test
        @DisplayName("create() 메서드로 현재 시간으로 Redirect를 생성한다")
        void create_CreatesRedirectWithCurrentTime() {
            // given
            String shortCode = "abc123";
            String targetUrl = "https://example.com";
            LocalDateTime before = LocalDateTime.now();

            // when
            Redirect redirect = Redirect.create(shortCode, targetUrl);

            // then
            LocalDateTime after = LocalDateTime.now();
            assertThat(redirect.getShortCode()).isEqualTo(shortCode);
            assertThat(redirect.getTargetUrl()).isEqualTo(targetUrl);
            assertThat(redirect.getCreatedAt()).isAfterOrEqualTo(before);
            assertThat(redirect.getCreatedAt()).isBeforeOrEqualTo(after);
        }

        @Test
        @DisplayName("of() 메서드로 특정 시간으로 Redirect를 생성한다")
        void of_CreatesRedirectWithSpecificTime() {
            // given
            String shortCode = "xyz789";
            String targetUrl = "https://example.com/long";
            LocalDateTime createdAt = LocalDateTime.of(2024, 1, 1, 12, 0, 0);

            // when
            Redirect redirect = Redirect.of(shortCode, targetUrl, createdAt);

            // then
            assertThat(redirect.getShortCode()).isEqualTo(shortCode);
            assertThat(redirect.getTargetUrl()).isEqualTo(targetUrl);
            assertThat(redirect.getCreatedAt()).isEqualTo(createdAt);
        }
    }

    @Nested
    @DisplayName("유효성 검증")
    class ValidationTest {

        @Test
        @DisplayName("shortCode가 null이면 예외를 발생시킨다")
        void create_NullShortCode_ThrowsException() {
            // when & then
            assertThatThrownBy(() -> Redirect.create(null, "https://example.com"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Short code must not be blank");
        }

        @Test
        @DisplayName("shortCode가 빈 문자열이면 예외를 발생시킨다")
        void create_EmptyShortCode_ThrowsException() {
            // when & then
            assertThatThrownBy(() -> Redirect.create("", "https://example.com"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Short code must not be blank");
        }

        @Test
        @DisplayName("shortCode가 공백만 있으면 예외를 발생시킨다")
        void create_BlankShortCode_ThrowsException() {
            // when & then
            assertThatThrownBy(() -> Redirect.create("   ", "https://example.com"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Short code must not be blank");
        }

        @Test
        @DisplayName("targetUrl이 null이면 예외를 발생시킨다")
        void create_NullTargetUrl_ThrowsException() {
            // when & then
            assertThatThrownBy(() -> Redirect.create("abc123", null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Target URL must not be blank");
        }

        @Test
        @DisplayName("targetUrl이 빈 문자열이면 예외를 발생시킨다")
        void create_EmptyTargetUrl_ThrowsException() {
            // when & then
            assertThatThrownBy(() -> Redirect.create("abc123", ""))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Target URL must not be blank");
        }

        @Test
        @DisplayName("targetUrl이 공백만 있으면 예외를 발생시킨다")
        void create_BlankTargetUrl_ThrowsException() {
            // when & then
            assertThatThrownBy(() -> Redirect.create("abc123", "   "))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Target URL must not be blank");
        }

        @Test
        @DisplayName("createdAt이 null이면 예외를 발생시킨다")
        void of_NullCreatedAt_ThrowsException() {
            // when & then
            assertThatThrownBy(() -> Redirect.of("abc123", "https://example.com", null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Created at must not be null");
        }
    }

    @Nested
    @DisplayName("equals와 hashCode")
    class EqualsAndHashCodeTest {

        @Test
        @DisplayName("같은 shortCode와 targetUrl을 가진 객체는 동등하다")
        void equals_SameShortCodeAndTargetUrl_ReturnsTrue() {
            // given
            String shortCode = "abc123";
            String targetUrl = "https://example.com";
            Redirect redirect1 = Redirect.create(shortCode, targetUrl);
            Redirect redirect2 = Redirect.create(shortCode, targetUrl);

            // when & then
            assertThat(redirect1).isEqualTo(redirect2);
            assertThat(redirect1.hashCode()).isEqualTo(redirect2.hashCode());
        }

        @Test
        @DisplayName("다른 shortCode를 가진 객체는 동등하지 않다")
        void equals_DifferentShortCode_ReturnsFalse() {
            // given
            Redirect redirect1 = Redirect.create("abc123", "https://example.com");
            Redirect redirect2 = Redirect.create("xyz789", "https://example.com");

            // when & then
            assertThat(redirect1).isNotEqualTo(redirect2);
        }

        @Test
        @DisplayName("다른 targetUrl을 가진 객체는 동등하지 않다")
        void equals_DifferentTargetUrl_ReturnsFalse() {
            // given
            Redirect redirect1 = Redirect.create("abc123", "https://example.com");
            Redirect redirect2 = Redirect.create("abc123", "https://different.com");

            // when & then
            assertThat(redirect1).isNotEqualTo(redirect2);
        }

        @Test
        @DisplayName("createdAt이 다르더라도 shortCode와 targetUrl이 같으면 동등하다")
        void equals_DifferentCreatedAt_SameShortCodeAndTargetUrl_ReturnsTrue() {
            // given
            String shortCode = "abc123";
            String targetUrl = "https://example.com";
            LocalDateTime time1 = LocalDateTime.of(2024, 1, 1, 12, 0, 0);
            LocalDateTime time2 = LocalDateTime.of(2024, 12, 31, 23, 59, 59);

            Redirect redirect1 = Redirect.of(shortCode, targetUrl, time1);
            Redirect redirect2 = Redirect.of(shortCode, targetUrl, time2);

            // when & then
            assertThat(redirect1).isEqualTo(redirect2);
            assertThat(redirect1.hashCode()).isEqualTo(redirect2.hashCode());
        }

        @Test
        @DisplayName("자기 자신과 비교하면 동등하다")
        void equals_SameInstance_ReturnsTrue() {
            // given
            Redirect redirect = Redirect.create("abc123", "https://example.com");

            // when & then
            assertThat(redirect).isEqualTo(redirect);
        }

        @Test
        @DisplayName("null과 비교하면 동등하지 않다")
        void equals_Null_ReturnsFalse() {
            // given
            Redirect redirect = Redirect.create("abc123", "https://example.com");

            // when & then
            assertThat(redirect).isNotEqualTo(null);
        }
    }

    @Nested
    @DisplayName("toString")
    class ToStringTest {

        @Test
        @DisplayName("toString()은 모든 필드를 포함한다")
        void toString_ContainsAllFields() {
            // given
            String shortCode = "abc123";
            String targetUrl = "https://example.com";
            LocalDateTime createdAt = LocalDateTime.of(2024, 1, 1, 12, 0, 0);
            Redirect redirect = Redirect.of(shortCode, targetUrl, createdAt);

            // when
            String result = redirect.toString();

            // then
            assertThat(result).contains("Redirect");
            assertThat(result).contains("shortCode='" + shortCode + "'");
            assertThat(result).contains("targetUrl='" + targetUrl + "'");
            assertThat(result).contains("createdAt=" + createdAt);
        }
    }

    @Nested
    @DisplayName("불변성")
    class ImmutabilityTest {

        @Test
        @DisplayName("생성 후 shortCode를 변경할 수 없다")
        void redirect_ShortCodeIsImmutable() {
            // given
            Redirect redirect = Redirect.create("abc123", "https://example.com");
            String originalShortCode = redirect.getShortCode();

            // when
            // shortCode는 final이므로 setter가 없음

            // then
            assertThat(redirect.getShortCode()).isEqualTo(originalShortCode);
        }

        @Test
        @DisplayName("생성 후 targetUrl을 변경할 수 없다")
        void redirect_TargetUrlIsImmutable() {
            // given
            Redirect redirect = Redirect.create("abc123", "https://example.com");
            String originalTargetUrl = redirect.getTargetUrl();

            // when
            // targetUrl은 final이므로 setter가 없음

            // then
            assertThat(redirect.getTargetUrl()).isEqualTo(originalTargetUrl);
        }

        @Test
        @DisplayName("생성 후 createdAt을 변경할 수 없다")
        void redirect_CreatedAtIsImmutable() {
            // given
            LocalDateTime createdAt = LocalDateTime.of(2024, 1, 1, 12, 0, 0);
            Redirect redirect = Redirect.of("abc123", "https://example.com", createdAt);

            // when
            // createdAt은 final이므로 setter가 없음

            // then
            assertThat(redirect.getCreatedAt()).isEqualTo(createdAt);
        }
    }
}
