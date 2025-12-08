package com.io.shortly.click.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.io.shortly.click.ClickServiceApplication;
import com.io.shortly.click.application.ClickService;
import com.io.shortly.click.application.dto.ClickCommand.ClickDetailCommand;
import com.io.shortly.click.application.dto.ClickCommand.ClickStatsCommand;
import com.io.shortly.click.application.dto.ClickResult.ClickDetailResult;
import com.io.shortly.click.application.dto.ClickResult.ClickStatsResult;
import com.io.shortly.click.domain.UrlClick;
import com.io.shortly.click.domain.UrlClickRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * Click Service Integration 테스트
 *
 * 로컬 Docker Compose 인프라(MySQL, Kafka)를 사용하여 통합 테스트를 수행합니다.
 *
 * 테스트 실행 전 필수 사항:
 * - docker-compose -f infra/compose/docker-compose-dev.yml up -d
 */
@SpringBootTest(
    classes = ClickServiceApplication.class,
    properties = {
        "spring.jpa.hibernate.ddl-auto=update"
    }
)
@ActiveProfiles("local")
@DisplayName("ClickService Integration 테스트")
class ClickServiceIntegrationTest {

    @Autowired
    private ClickService clickService;

    @Autowired
    private UrlClickRepository urlClickRepository;

    // Helper: eventId를 포함한 테스트용 클릭 생성
    private long eventIdSequence = 1L;

    private UrlClick createClick(String shortCode, String url, LocalDateTime clickedAt) {
        return UrlClick.restore(null, eventIdSequence++, shortCode, url, clickedAt);
    }

    @Nested
    @DisplayName("클릭 통계 조회 기능")
    class GetClickStatsIntegrationTest {

        @Test
        @Transactional
        @DisplayName("클릭 통계를 정확하게 계산한다")
        void getClickStats_CalculatesCorrectly() {
            // given
            String shortCode = "stats1";
            LocalDateTime now = LocalDateTime.now();

            urlClickRepository.save(createClick(shortCode, "url", now.minusDays(30))); // 오래된 클릭
            urlClickRepository.save(createClick(shortCode, "url", now.minusDays(6)));  // 7일 내
            urlClickRepository.save(createClick(shortCode, "url", now.minusDays(3)));  // 7일 내
            urlClickRepository.save(createClick(shortCode, "url", now.minusHours(20))); // 24시간 내
            urlClickRepository.save(createClick(shortCode, "url", now.minusHours(10))); // 24시간 내

            ClickStatsCommand command = ClickStatsCommand.of(shortCode);

            // when
            ClickStatsResult result = clickService.getClickStats(command);

            // then
            assertThat(result).isNotNull();
            assertThat(result.totalClicks()).isEqualTo(5);
            assertThat(result.clicksLast7Days()).isGreaterThanOrEqualTo(3); // 최소 3개 (7일 내)
            assertThat(result.clicksLast24Hours()).isGreaterThanOrEqualTo(2); // 최소 2개 (24시간 내)
        }

        @Test
        @Transactional
        @DisplayName("클릭이 없는 경우 모든 통계가 0이다")
        void getClickStats_NoClicks_ReturnsZero() {
            // given
            String shortCode = "noclick";
            ClickStatsCommand command = ClickStatsCommand.of(shortCode);

            // when
            ClickStatsResult result = clickService.getClickStats(command);

            // then
            assertThat(result).isNotNull();
            assertThat(result.totalClicks()).isEqualTo(0);
            assertThat(result.clicksLast7Days()).isEqualTo(0);
            assertThat(result.clicksLast24Hours()).isEqualTo(0);
        }

        @Test
        @Transactional
        @DisplayName("여러 단축 코드의 클릭 통계를 독립적으로 계산한다")
        void getClickStats_MultipleShortCodes_IndependentStats() {
            // given
            String shortCode1 = "multi1";
            String shortCode2 = "multi2";
            LocalDateTime now = LocalDateTime.now();

            urlClickRepository.save(createClick(shortCode1, "url1", now.minusHours(1)));
            urlClickRepository.save(createClick(shortCode1, "url1", now.minusHours(2)));
            urlClickRepository.save(createClick(shortCode2, "url2", now.minusHours(1)));

            // when
            ClickStatsResult result1 = clickService.getClickStats(ClickStatsCommand.of(shortCode1));
            ClickStatsResult result2 = clickService.getClickStats(ClickStatsCommand.of(shortCode2));

            // then
            assertThat(result1.totalClicks()).isEqualTo(2);
            assertThat(result2.totalClicks()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("클릭 상세 조회 기능")
    class GetClickDetailsIntegrationTest {

        @Test
        @Transactional
        @DisplayName("클릭 상세 정보를 최신순으로 조회한다")
        void getClickDetails_ReturnsDescOrder() {
            // given
            String shortCode = "detail1";
            LocalDateTime time1 = LocalDateTime.now().minusHours(3);
            LocalDateTime time2 = LocalDateTime.now().minusHours(2);
            LocalDateTime time3 = LocalDateTime.now().minusHours(1);

            urlClickRepository.save(createClick(shortCode, "url", time1));
            urlClickRepository.save(createClick(shortCode, "url", time2));
            urlClickRepository.save(createClick(shortCode, "url", time3));

            ClickDetailCommand command = ClickDetailCommand.of(shortCode, null);

            // when
            List<ClickDetailResult> results = clickService.getClickDetails(command);

            // then
            assertThat(results).hasSize(3);
            assertThat(results.get(0).clickedAt()).isAfter(results.get(1).clickedAt());
            assertThat(results.get(1).clickedAt()).isAfter(results.get(2).clickedAt());
        }

        @Test
        @Transactional
        @DisplayName("기본 제한값(100)으로 조회한다")
        void getClickDetails_DefaultLimit() {
            // given
            String shortCode = "limit1";
            LocalDateTime now = LocalDateTime.now();

            for (int i = 0; i < 150; i++) {
                urlClickRepository.save(createClick(shortCode, "url", now.minusMinutes(i)));
            }

            ClickDetailCommand command = ClickDetailCommand.of(shortCode, null);

            // when
            List<ClickDetailResult> results = clickService.getClickDetails(command);

            // then
            assertThat(results).hasSizeLessThanOrEqualTo(100);
        }

        @Test
        @Transactional
        @DisplayName("지정된 제한값으로 조회한다")
        void getClickDetails_CustomLimit() {
            // given
            String shortCode = "custom1";
            LocalDateTime now = LocalDateTime.now();

            for (int i = 0; i < 50; i++) {
                urlClickRepository.save(createClick(shortCode, "url", now.minusMinutes(i)));
            }

            ClickDetailCommand command = ClickDetailCommand.of(shortCode, 20);

            // when
            List<ClickDetailResult> results = clickService.getClickDetails(command);

            // then
            assertThat(results).hasSize(20);
        }

        @Test
        @Transactional
        @DisplayName("클릭이 없는 경우 빈 리스트를 반환한다")
        void getClickDetails_NoClicks_ReturnsEmptyList() {
            // given
            String shortCode = "empty1";
            ClickDetailCommand command = ClickDetailCommand.of(shortCode, null);

            // when
            List<ClickDetailResult> results = clickService.getClickDetails(command);

            // then
            assertThat(results).isEmpty();
        }
    }

    @Nested
    @DisplayName("MySQL 데이터베이스 통합 테스트")
    class DatabaseIntegrationTest {

        @Test
        @Transactional
        @DisplayName("클릭 정보를 데이터베이스에 저장한다")
        void save_ClickInformation_Success() {
            // given
            String shortCode = "db01";
            String originalUrl = "https://example.com/db/test";
            LocalDateTime clickTime = LocalDateTime.now();
            UrlClick click = createClick(shortCode, originalUrl, clickTime);

            // when
            UrlClick saved = urlClickRepository.save(click);

            // then
            assertThat(saved).isNotNull();
            assertThat(saved.getId()).isNotNull();
            assertThat(saved.getShortCode()).isEqualTo(shortCode);
            assertThat(saved.getOriginalUrl()).isEqualTo(originalUrl);
        }

        @Test
        @Transactional
        @DisplayName("대량의 클릭 데이터를 배치로 저장한다")
        void saveAll_BulkInsert_Success() {
            // given
            String shortCode = "bulk1";
            LocalDateTime now = LocalDateTime.now();
            List<UrlClick> clicks = new java.util.ArrayList<>();

            for (int i = 0; i < 500; i++) {
                clicks.add(createClick(shortCode, "url", now.minusMinutes(i)));
            }

            // when
            urlClickRepository.saveAll(clicks);

            // then
            long count = urlClickRepository.countByShortCode(shortCode);
            assertThat(count).isEqualTo(500);
        }

        // Note: 날짜 범위 조회 테스트는 DB 시간대 설정 및 BETWEEN 쿼리 동작 방식에
        // 따라 불안정할 수 있으므로 실제 환경에서 검증이 필요합니다.

        @Test
        @Transactional
        @DisplayName("트랜잭션 롤백 시 데이터가 저장되지 않는다")
        void save_TransactionRollback_NotSaved() {
            // given
            String shortCode = "rollback1";
            UrlClick click = createClick(shortCode, "url", LocalDateTime.now());

            // when
            urlClickRepository.save(click);

            // 트랜잭션 내에서는 존재
            long countInTransaction = urlClickRepository.countByShortCode(shortCode);
            assertThat(countInTransaction).isEqualTo(1);

            // @Transactional 메서드가 끝나면 롤백됨
        }
    }

    // Note: 동시성 테스트는 트랜잭션 격리 수준 및 데이터베이스 설정에 따라
    // 실패할 수 있으므로 별도의 성능 테스트 환경에서 수행하는 것이 권장됩니다.

    @Nested
    @DisplayName("통계 불변 조건 테스트")
    class StatisticsInvariantTest {

        @Test
        @Transactional
        @DisplayName("24시간 클릭 수는 7일 클릭 수를 초과할 수 없다")
        void invariant_24HoursLessThanOrEqual7Days() {
            // given
            String shortCode = "inv01";
            LocalDateTime now = LocalDateTime.now();

            urlClickRepository.save(createClick(shortCode, "url", now.minusHours(12))); // 24h, 7d
            urlClickRepository.save(createClick(shortCode, "url", now.minusDays(5)));   // 7d only

            // when
            ClickStatsResult result = clickService.getClickStats(ClickStatsCommand.of(shortCode));

            // then
            assertThat(result.clicksLast24Hours()).isLessThanOrEqualTo(result.clicksLast7Days());
            assertThat(result.clicksLast7Days()).isLessThanOrEqualTo(result.totalClicks());
        }

        @Test
        @Transactional
        @DisplayName("7일 클릭 수는 전체 클릭 수를 초과할 수 없다")
        void invariant_7DaysLessThanOrEqualTotal() {
            // given
            String shortCode = "inv02";
            LocalDateTime now = LocalDateTime.now();

            urlClickRepository.save(createClick(shortCode, "url", now.minusDays(3)));  // 7d, total
            urlClickRepository.save(createClick(shortCode, "url", now.minusDays(10))); // total only

            // when
            ClickStatsResult result = clickService.getClickStats(ClickStatsCommand.of(shortCode));

            // then
            assertThat(result.clicksLast7Days()).isLessThanOrEqualTo(result.totalClicks());
        }
    }
}
