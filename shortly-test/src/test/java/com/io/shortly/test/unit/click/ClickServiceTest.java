package com.io.shortly.test.unit.click;

import static org.junit.jupiter.api.Assertions.*;

import com.io.shortly.click.application.ClickService;
import com.io.shortly.click.application.dto.ClickCommand.ClickStatsCommand;
import com.io.shortly.click.application.dto.ClickResult.ClickStatsResult;
import com.io.shortly.click.domain.UrlClick;
import com.io.shortly.test.unit.click.mock.FakeUrlClickRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ClickService 비즈니스 로직 단위 테스트")
class ClickServiceTest {

    private ClickService clickService;
    private FakeUrlClickRepository repository;

    @BeforeEach
    void setUp() {
        repository = new FakeUrlClickRepository();
        clickService = new ClickService(repository);
    }

    @Test
    @DisplayName("클릭 통계 조회 - 클릭 데이터가 없는 경우")
    void getClickStats_NoClicks() {
        // given
        String shortCode = "abc123";
        ClickStatsCommand command = new ClickStatsCommand(shortCode);

        // when
        ClickStatsResult result = clickService.getClickStats(command);

        // then
        assertNotNull(result);
        assertEquals(shortCode, result.shortCode());
        assertEquals(0L, result.totalClicks());
        assertEquals(0L, result.clicksLast24Hours());
        assertEquals(0L, result.clicksLast7Days());
    }

    @Test
    @DisplayName("클릭 통계 조회 - 24시간 내 클릭만 있는 경우")
    void getClickStats_OnlyLast24Hours() {
        // given
        String shortCode = "abc123";
        LocalDateTime now = LocalDateTime.now();

        // 최근 24시간 내 클릭 5개
        saveClickAt(shortCode, now.minusHours(1));
        saveClickAt(shortCode, now.minusHours(5));
        saveClickAt(shortCode, now.minusHours(10));
        saveClickAt(shortCode, now.minusHours(15));
        saveClickAt(shortCode, now.minusHours(20));

        ClickStatsCommand command = new ClickStatsCommand(shortCode);

        // when
        ClickStatsResult result = clickService.getClickStats(command);

        // then
        assertEquals(5L, result.totalClicks());
        assertEquals(5L, result.clicksLast24Hours());
        assertEquals(5L, result.clicksLast7Days());
    }

    @Test
    @DisplayName("클릭 통계 조회 - 7일 내 클릭 (24시간 초과)")
    void getClickStats_Last7Days() {
        // given
        String shortCode = "abc123";
        LocalDateTime now = LocalDateTime.now();

        // 24시간 내 클릭 3개
        saveClickAt(shortCode, now.minusHours(5));
        saveClickAt(shortCode, now.minusHours(10));
        saveClickAt(shortCode, now.minusHours(20));

        // 24시간~7일 사이 클릭 4개
        saveClickAt(shortCode, now.minusDays(2));
        saveClickAt(shortCode, now.minusDays(3));
        saveClickAt(shortCode, now.minusDays(5));
        saveClickAt(shortCode, now.minusDays(6));

        ClickStatsCommand command = new ClickStatsCommand(shortCode);

        // when
        ClickStatsResult result = clickService.getClickStats(command);

        // then
        assertEquals(7L, result.totalClicks());
        assertEquals(3L, result.clicksLast24Hours());
        assertEquals(7L, result.clicksLast7Days());
    }

    @Test
    @DisplayName("클릭 통계 조회 - 전체 기간 클릭")
    void getClickStats_AllPeriods() {
        // given
        String shortCode = "abc123";
        LocalDateTime now = LocalDateTime.now();

        // 24시간 내 클릭 2개
        saveClickAt(shortCode, now.minusHours(1));
        saveClickAt(shortCode, now.minusHours(12));

        // 24시간~7일 클릭 3개
        saveClickAt(shortCode, now.minusDays(2));
        saveClickAt(shortCode, now.minusDays(4));
        saveClickAt(shortCode, now.minusDays(6));

        // 7일 초과 클릭 5개
        saveClickAt(shortCode, now.minusDays(10));
        saveClickAt(shortCode, now.minusDays(15));
        saveClickAt(shortCode, now.minusDays(30));
        saveClickAt(shortCode, now.minusDays(60));
        saveClickAt(shortCode, now.minusDays(90));

        ClickStatsCommand command = new ClickStatsCommand(shortCode);

        // when
        ClickStatsResult result = clickService.getClickStats(command);

        // then
        assertEquals(10L, result.totalClicks());
        assertEquals(2L, result.clicksLast24Hours());
        assertEquals(5L, result.clicksLast7Days());
    }

    @Test
    @DisplayName("클릭 통계 조회 - 오래된 클릭만 있는 경우")
    void getClickStats_OnlyOldClicks() {
        // given
        String shortCode = "abc123";
        LocalDateTime now = LocalDateTime.now();

        // 7일 초과 클릭만
        saveClickAt(shortCode, now.minusDays(10));
        saveClickAt(shortCode, now.minusDays(20));
        saveClickAt(shortCode, now.minusDays(30));

        ClickStatsCommand command = new ClickStatsCommand(shortCode);

        // when
        ClickStatsResult result = clickService.getClickStats(command);

        // then
        assertEquals(3L, result.totalClicks());
        assertEquals(0L, result.clicksLast24Hours());
        assertEquals(0L, result.clicksLast7Days());
    }

    @Test
    @DisplayName("클릭 통계 조회 - 경계값 테스트 (정확히 24시간 전)")
    void getClickStats_BoundaryTest_Exactly24Hours() {
        // given
        String shortCode = "abc123";
        LocalDateTime now = LocalDateTime.now();

        // 정확히 24시간 전 클릭 (isAfter는 초과이므로 포함 안 됨)
        saveClickAt(shortCode, now.minusHours(24));

        // 24시간 - 1초 전 클릭 (24시간 이내이므로 포함됨)
        saveClickAt(shortCode, now.minusHours(24).plusSeconds(1));

        // 24시간 + 1초 전 클릭 (24시간 초과이므로 포함 안 됨)
        saveClickAt(shortCode, now.minusHours(24).minusSeconds(1));

        ClickStatsCommand command = new ClickStatsCommand(shortCode);

        // when
        ClickStatsResult result = clickService.getClickStats(command);

        // then
        assertEquals(3L, result.totalClicks());
        assertEquals(1L, result.clicksLast24Hours()); // 24시간 - 1초만 포함
        assertEquals(3L, result.clicksLast7Days());
    }

    @Test
    @DisplayName("클릭 통계 조회 - 경계값 테스트 (정확히 7일 전)")
    void getClickStats_BoundaryTest_Exactly7Days() {
        // given
        String shortCode = "abc123";
        LocalDateTime now = LocalDateTime.now();

        // 7일 - 10초 전 클릭 (7일 이내, 여유를 둠)
        saveClickAt(shortCode, now.minusDays(7).plusSeconds(10));

        // 7일 - 1시간 전 클릭 (7일 이내)
        saveClickAt(shortCode, now.minusDays(7).plusHours(1));

        // 7일 + 1시간 전 클릭 (7일 초과)
        saveClickAt(shortCode, now.minusDays(7).minusHours(1));

        // 8일 전 클릭 (7일 초과)
        saveClickAt(shortCode, now.minusDays(8));

        ClickStatsCommand command = new ClickStatsCommand(shortCode);

        // when
        ClickStatsResult result = clickService.getClickStats(command);

        // then
        assertEquals(4L, result.totalClicks());
        assertEquals(0L, result.clicksLast24Hours());
        assertEquals(2L, result.clicksLast7Days()); // 7일 - 10초, 7일 - 1시간
    }

    @Test
    @DisplayName("클릭 통계 조회 - 여러 ShortCode 독립성 검증")
    void getClickStats_MultipleShortCodes_Independent() {
        // given
        LocalDateTime now = LocalDateTime.now();

        // abc123: 총 5개 클릭
        saveClickAt("abc123", now.minusHours(1));
        saveClickAt("abc123", now.minusHours(5));
        saveClickAt("abc123", now.minusDays(2));
        saveClickAt("abc123", now.minusDays(4));
        saveClickAt("abc123", now.minusDays(10));

        // xyz789: 총 3개 클릭
        saveClickAt("xyz789", now.minusHours(2));
        saveClickAt("xyz789", now.minusDays(3));
        saveClickAt("xyz789", now.minusDays(20));

        // when
        ClickStatsResult result1 = clickService.getClickStats(new ClickStatsCommand("abc123"));
        ClickStatsResult result2 = clickService.getClickStats(new ClickStatsCommand("xyz789"));

        // then
        assertEquals(5L, result1.totalClicks());
        assertEquals(2L, result1.clicksLast24Hours());
        assertEquals(4L, result1.clicksLast7Days());

        assertEquals(3L, result2.totalClicks());
        assertEquals(1L, result2.clicksLast24Hours());
        assertEquals(2L, result2.clicksLast7Days());
    }

    @Test
    @DisplayName("클릭 통계 조회 - 대량 데이터 처리")
    void getClickStats_LargeDataSet() {
        // given
        String shortCode = "popular";
        LocalDateTime now = LocalDateTime.now();

        // 24시간 내 100개
        for (int i = 0; i < 100; i++) {
            saveClickAt(shortCode, now.minusHours(i % 24));
        }

        // 7일 내 200개
        for (int i = 0; i < 200; i++) {
            saveClickAt(shortCode, now.minusDays(i % 7));
        }

        // 오래된 클릭 300개
        for (int i = 0; i < 300; i++) {
            saveClickAt(shortCode, now.minusDays(10 + (i % 90)));
        }

        ClickStatsCommand command = new ClickStatsCommand(shortCode);

        // when
        ClickStatsResult result = clickService.getClickStats(command);

        // then
        assertEquals(600L, result.totalClicks());
        assertTrue(result.clicksLast24Hours() >= 100);
        assertTrue(result.clicksLast7Days() >= 300);
    }

    // Helper method
    private void saveClickAt(String shortCode, LocalDateTime clickedAt) {
        UrlClick click = UrlClick.restore(
            null,
            System.nanoTime(),
            shortCode,
            "https://example.com",
            clickedAt
        );
        repository.save(click);
    }
}
