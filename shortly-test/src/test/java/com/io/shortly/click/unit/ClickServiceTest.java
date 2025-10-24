package com.io.shortly.click.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.io.shortly.click.application.ClickService;
import com.io.shortly.click.application.dto.ClickCommand.ClickDetailCommand;
import com.io.shortly.click.application.dto.ClickCommand.ClickStatsCommand;
import com.io.shortly.click.application.dto.ClickResult.ClickDetailResult;
import com.io.shortly.click.application.dto.ClickResult.ClickStatsResult;
import com.io.shortly.click.domain.UrlClick;
import com.io.shortly.click.domain.UrlClickRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Click Service 단위 테스트")
class ClickServiceTest {

    private ClickService clickService;
    private MockUrlClickRepository mockRepository;

    @BeforeEach
    void setUp() {
        mockRepository = new MockUrlClickRepository();
        clickService = new ClickService(mockRepository);
    }

    @Test
    @DisplayName("클릭 통계를 정상적으로 조회한다")
    void getClickStats_ValidShortCode_ReturnsStats() {
        // Given
        String shortCode = "abc123";
        LocalDateTime now = LocalDateTime.now();

        mockRepository.addClick(shortCode, now.minusDays(30)); // Old click
        mockRepository.addClick(shortCode, now.minusDays(5));  // Within 7 days
        mockRepository.addClick(shortCode, now.minusDays(3));  // Within 7 days
        mockRepository.addClick(shortCode, now.minusHours(12)); // Within 24 hours
        mockRepository.addClick(shortCode, now.minusHours(6));  // Within 24 hours

        ClickStatsCommand command = ClickStatsCommand.of(shortCode);

        // When
        ClickStatsResult result = clickService.getClickStats(command);

        // Then
        assertThat(result.shortCode()).isEqualTo(shortCode);
        assertThat(result.totalClicks()).isEqualTo(5);
        assertThat(result.clicksLast7Days()).isEqualTo(4); // Excludes 30-day old
        assertThat(result.clicksLast24Hours()).isEqualTo(2);
    }

    @Test
    @DisplayName("클릭이 없는 경우 0을 반환한다")
    void getClickStats_NoClicks_ReturnsZero() {
        // Given
        String shortCode = "noclick";
        ClickStatsCommand command = ClickStatsCommand.of(shortCode);

        // When
        ClickStatsResult result = clickService.getClickStats(command);

        // Then
        assertThat(result.totalClicks()).isEqualTo(0);
        assertThat(result.clicksLast7Days()).isEqualTo(0);
        assertThat(result.clicksLast24Hours()).isEqualTo(0);
    }

    @Test
    @DisplayName("클릭 상세 정보를 조회한다")
    void getClickDetails_ValidShortCode_ReturnsDetails() {
        // Given
        String shortCode = "xyz789";
        LocalDateTime time1 = LocalDateTime.now().minusHours(10);
        LocalDateTime time2 = LocalDateTime.now().minusHours(5);
        LocalDateTime time3 = LocalDateTime.now().minusHours(1);

        mockRepository.addClick(shortCode, time1);
        mockRepository.addClick(shortCode, time2);
        mockRepository.addClick(shortCode, time3);

        ClickDetailCommand command = ClickDetailCommand.of(shortCode, null);

        // When
        List<ClickDetailResult> results = clickService.getClickDetails(command);

        // Then
        assertThat(results).hasSize(3);
        assertThat(results.get(0).clickedAt()).isEqualTo(time3); // Most recent first
        assertThat(results.get(1).clickedAt()).isEqualTo(time2);
        assertThat(results.get(2).clickedAt()).isEqualTo(time1);
    }

    @Test
    @DisplayName("limit을 지정하면 해당 개수만큼만 반환한다")
    void getClickDetails_WithLimit_ReturnsLimitedResults() {
        // Given
        String shortCode = "limit01";
        for (int i = 0; i < 10; i++) {
            mockRepository.addClick(shortCode, LocalDateTime.now().minusHours(i));
        }

        ClickDetailCommand command = ClickDetailCommand.of(shortCode, 5);

        // When
        List<ClickDetailResult> results = clickService.getClickDetails(command);

        // Then
        assertThat(results).hasSize(5);
        assertThat(mockRepository.lastRequestedLimit).isEqualTo(5);
    }

    @Test
    @DisplayName("limit이 null이면 기본값 100을 사용한다")
    void getClickDetails_NullLimit_UsesDefaultLimit() {
        // Given
        String shortCode = "default";
        mockRepository.addClick(shortCode, LocalDateTime.now());

        ClickDetailCommand command = ClickDetailCommand.of(shortCode, null);

        // When
        clickService.getClickDetails(command);

        // Then
        assertThat(mockRepository.lastRequestedLimit).isEqualTo(100);
    }

    @Test
    @DisplayName("빈 단축 코드는 예외를 발생시킨다")
    void getClickStats_EmptyShortCode_ThrowsException() {
        // Given
        ClickStatsCommand command = ClickStatsCommand.of("");

        // When & Then
        assertThatThrownBy(() -> clickService.getClickStats(command))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Short code must not be blank");
    }

    @Test
    @DisplayName("잘못된 형식의 단축 코드는 예외를 발생시킨다")
    void getClickStats_InvalidFormat_ThrowsException() {
        // Given - short code with special characters
        ClickStatsCommand command = ClickStatsCommand.of("abc@#$");

        // When & Then
        assertThatThrownBy(() -> clickService.getClickStats(command))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("6-10 alphanumeric characters");
    }

    @Test
    @DisplayName("단축 코드가 너무 짧으면 예외를 발생시킨다")
    void getClickStats_TooShortCode_ThrowsException() {
        // Given
        ClickStatsCommand command = ClickStatsCommand.of("abc12"); // 5 chars

        // When & Then
        assertThatThrownBy(() -> clickService.getClickStats(command))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("단축 코드가 너무 길면 예외를 발생시킨다")
    void getClickStats_TooLongCode_ThrowsException() {
        // Given
        ClickStatsCommand command = ClickStatsCommand.of("abc12345678"); // 11 chars

        // When & Then
        assertThatThrownBy(() -> clickService.getClickStats(command))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("limit이 0이면 예외를 발생시킨다")
    void getClickDetails_ZeroLimit_ThrowsException() {
        // Given
        ClickDetailCommand command = ClickDetailCommand.of("abc123", 0);

        // When & Then
        assertThatThrownBy(() -> clickService.getClickDetails(command))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Limit must be positive");
    }

    @Test
    @DisplayName("limit이 음수면 예외를 발생시킨다")
    void getClickDetails_NegativeLimit_ThrowsException() {
        // Given
        ClickDetailCommand command = ClickDetailCommand.of("abc123", -1);

        // When & Then
        assertThatThrownBy(() -> clickService.getClickDetails(command))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Limit must be positive");
    }

    @Test
    @DisplayName("limit이 최대값을 초과하면 예외를 발생시킨다")
    void getClickDetails_LimitExceedsMax_ThrowsException() {
        // Given
        ClickDetailCommand command = ClickDetailCommand.of("abc123", 1001); // MAX_LIMIT = 1000

        // When & Then
        assertThatThrownBy(() -> clickService.getClickDetails(command))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Limit must not exceed 1000");
    }

    @Test
    @DisplayName("null 커맨드는 예외를 발생시킨다")
    void getClickStats_NullCommand_ThrowsException() {
        // When & Then
        assertThatThrownBy(() -> clickService.getClickStats(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Command must not be null");
    }

    @Test
    @DisplayName("통계 불변 조건을 검증한다 - 24시간 클릭 수는 7일 클릭 수를 초과할 수 없다")
    void getClickStats_Invariants_24HoursLessThan7Days() {
        // Given
        String shortCode = "inv123";
        LocalDateTime now = LocalDateTime.now();

        mockRepository.addClick(shortCode, now.minusHours(12)); // Within 24h
        mockRepository.addClick(shortCode, now.minusDays(5));   // Within 7d but not 24h

        ClickStatsCommand command = ClickStatsCommand.of(shortCode);

        // When
        ClickStatsResult result = clickService.getClickStats(command);

        // Then - Invariant holds
        assertThat(result.clicksLast24Hours()).isLessThanOrEqualTo(result.clicksLast7Days());
        assertThat(result.clicksLast7Days()).isLessThanOrEqualTo(result.totalClicks());
    }

    // ========== Mock Implementation ==========

    /**
     * Mock UrlClickRepository - 클릭 데이터 저장소 시뮬레이션
     */
    private static class MockUrlClickRepository implements UrlClickRepository {
        private final List<UrlClick> clicks = new ArrayList<>();
        private long idSequence = 1L;
        Integer lastRequestedLimit = null;

        void addClick(String shortCode, LocalDateTime clickedAt) {
            UrlClick click = UrlClick.restore(idSequence++, shortCode, "https://example.com", clickedAt);
            clicks.add(click);
        }

        @Override
        public List<UrlClick> findByShortCode(String shortCode) {
            return clicks.stream()
                .filter(click -> click.getShortCode().equals(shortCode))
                .sorted((a, b) -> b.getClickedAt().compareTo(a.getClickedAt()))
                .collect(Collectors.toList());
        }

        @Override
        public long countByShortCode(String shortCode) {
            return clicks.stream()
                .filter(click -> click.getShortCode().equals(shortCode))
                .count();
        }

        @Override
        public List<UrlClick> findByShortCodeAndClickedAtBetween(
            String shortCode,
            LocalDateTime startDate,
            LocalDateTime endDate
        ) {
            return clicks.stream()
                .filter(click -> click.getShortCode().equals(shortCode))
                .filter(click -> !click.getClickedAt().isBefore(startDate))
                .filter(click -> !click.getClickedAt().isAfter(endDate))
                .collect(Collectors.toList());
        }

        @Override
        public List<UrlClick> findByShortCodeWithLimit(String shortCode, int limit) {
            lastRequestedLimit = limit;
            return clicks.stream()
                .filter(click -> click.getShortCode().equals(shortCode))
                .sorted((a, b) -> b.getClickedAt().compareTo(a.getClickedAt())) // DESC order
                .limit(limit)
                .collect(Collectors.toList());
        }

        @Override
        public UrlClick save(UrlClick urlClick) {
            clicks.add(urlClick);
            return urlClick;
        }
    }
}
