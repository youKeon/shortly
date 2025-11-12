package com.io.shortly.click.unit;

import com.io.shortly.click.domain.ClickEventDLQPublisher;
import com.io.shortly.click.domain.UrlClick;
import com.io.shortly.click.domain.UrlClickRepository;
import com.io.shortly.click.infrastructure.event.kafka.UrlClickedEventConsumer;
import com.io.shortly.shared.event.UrlClickedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("UrlClickedEventConsumer 단위 테스트")
class UrlClickedEventConsumerTest {

    @Nested
    @DisplayName("배치 이벤트 처리")
    class ConsumeBatchTest {

        @Test
        @DisplayName("배치로 받은 이벤트를 Bulk Insert로 처리한다")
        void consumeUrlClickedBatch_BulkInsertSuccess() {
            // given
            FakeUrlClickRepository repository = new FakeUrlClickRepository();
            SpyClickEventDLQPublisher dlqPublisher = new SpyClickEventDLQPublisher();
            UrlClickedEventConsumer consumer = new UrlClickedEventConsumer(repository, dlqPublisher);

            List<UrlClickedEvent> events = List.of(
                UrlClickedEvent.of("code1", "url1"),
                UrlClickedEvent.of("code2", "url2"),
                UrlClickedEvent.of("code3", "url3")
            );

            // when
            consumer.consumeUrlClickedBatch(events);

            // then
            assertThat(repository.getSavedClicks()).hasSize(3);
            assertThat(dlqPublisher.getDlqEvents()).isEmpty();
        }

        @Test
        @DisplayName("빈 이벤트 리스트는 처리하지 않는다")
        void consumeUrlClickedBatch_EmptyList() {
            // given
            FakeUrlClickRepository repository = new FakeUrlClickRepository();
            SpyClickEventDLQPublisher dlqPublisher = new SpyClickEventDLQPublisher();
            UrlClickedEventConsumer consumer = new UrlClickedEventConsumer(repository, dlqPublisher);

            List<UrlClickedEvent> events = List.of();

            // when
            consumer.consumeUrlClickedBatch(events);

            // then
            assertThat(repository.getSavedClicks()).isEmpty();
            assertThat(repository.getSaveAllCallCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("Bulk Insert 실패 시 개별 처리로 전환한다")
        void consumeUrlClickedBatch_FallbackToIndividualProcessing() {
            // given
            FailingOnceRepository repository = new FailingOnceRepository();
            SpyClickEventDLQPublisher dlqPublisher = new SpyClickEventDLQPublisher();
            UrlClickedEventConsumer consumer = new UrlClickedEventConsumer(repository, dlqPublisher);

            List<UrlClickedEvent> events = List.of(
                UrlClickedEvent.of("code1", "url1"),
                UrlClickedEvent.of("code2", "url2")
            );

            // when
            consumer.consumeUrlClickedBatch(events);

            // then
            assertThat(repository.getIndividualSavedClicks()).hasSize(2); // 개별 처리로 성공
            assertThat(repository.getSaveAllCallCount()).isEqualTo(1); // Bulk 1번 시도
            assertThat(repository.getSaveCallCount()).isEqualTo(2); // 개별 2번 처리
        }
    }

    @Nested
    @DisplayName("개별 이벤트 재시도 처리")
    class RetryTest {

        @Test
        @DisplayName("개별 이벤트 처리 실패 시 최대 3번 재시도한다")
        void processWithRetry_RetriesUpToMaxAttempts() {
            // given
            AlwaysFailingRepository repository = new AlwaysFailingRepository();
            SpyClickEventDLQPublisher dlqPublisher = new SpyClickEventDLQPublisher();
            UrlClickedEventConsumer consumer = new UrlClickedEventConsumer(repository, dlqPublisher);

            List<UrlClickedEvent> events = List.of(
                UrlClickedEvent.of("code1", "url1")
            );

            // when
            consumer.consumeUrlClickedBatch(events);

            // then
            assertThat(repository.getSaveCallCount()).isEqualTo(3); // 개별 3번 재시도
            assertThat(dlqPublisher.getDlqEvents()).hasSize(1); // DLQ로 전송
        }

        @Test
        @DisplayName("재시도 중 성공하면 DLQ로 전송하지 않는다")
        void processWithRetry_SucceedsOnSecondAttempt() {
            // given
            FailingTwiceRepository repository = new FailingTwiceRepository();
            SpyClickEventDLQPublisher dlqPublisher = new SpyClickEventDLQPublisher();
            UrlClickedEventConsumer consumer = new UrlClickedEventConsumer(repository, dlqPublisher);

            List<UrlClickedEvent> events = List.of(
                UrlClickedEvent.of("code1", "url1")
            );

            // when
            consumer.consumeUrlClickedBatch(events);

            // then
            assertThat(repository.getIndividualSavedClicks()).hasSize(3); // 3번 시도 (마지막 성공)
            assertThat(repository.getSaveCallCount()).isEqualTo(3); // 개별 3번 (3번째 성공)
            assertThat(dlqPublisher.getDlqEvents()).isEmpty(); // DLQ 전송 안 함
        }
    }

    @Nested
    @DisplayName("DLQ 전송")
    class DLQTest {

        @Test
        @DisplayName("최대 재시도 실패 후 DLQ로 이벤트를 전송한다")
        void publishToDLQ_AfterMaxRetries() {
            // given
            AlwaysFailingRepository repository = new AlwaysFailingRepository();
            SpyClickEventDLQPublisher dlqPublisher = new SpyClickEventDLQPublisher();
            UrlClickedEventConsumer consumer = new UrlClickedEventConsumer(repository, dlqPublisher);

            UrlClickedEvent event = UrlClickedEvent.of("failed", "url");
            List<UrlClickedEvent> events = List.of(event);

            // when
            consumer.consumeUrlClickedBatch(events);

            // then
            assertThat(dlqPublisher.getDlqEvents()).hasSize(1);
            assertThat(dlqPublisher.getDlqEvents().get(0).event()).isEqualTo(event);
            assertThat(dlqPublisher.getDlqEvents().get(0).retryCount()).isEqualTo(3);
        }

        @Test
        @DisplayName("배치 내 일부만 실패하면 실패한 이벤트만 DLQ로 전송한다")
        void publishToDLQ_OnlyFailedEvents() {
            // given
            SelectiveFailingRepository repository = new SelectiveFailingRepository("code2");
            SpyClickEventDLQPublisher dlqPublisher = new SpyClickEventDLQPublisher();
            UrlClickedEventConsumer consumer = new UrlClickedEventConsumer(repository, dlqPublisher);

            List<UrlClickedEvent> events = List.of(
                UrlClickedEvent.of("code1", "url1"),
                UrlClickedEvent.of("code2", "url2"), // 실패
                UrlClickedEvent.of("code3", "url3")
            );

            // when
            consumer.consumeUrlClickedBatch(events);

            // then
            // code2는 3번 재시도하므로 총 6번 save 호출 (code1 1번, code2 3번, code3 2번)
            assertThat(repository.getIndividualSavedClicks()
                .stream()
                .filter(click -> !click.getShortCode().equals("code2"))
                .count()).isEqualTo(2); // code1, code3만 성공
            assertThat(dlqPublisher.getDlqEvents()).hasSize(1); // code2만 DLQ
            assertThat(dlqPublisher.getDlqEvents().get(0).event().getShortCode()).isEqualTo("code2");
        }
    }

    // ==================== Test Doubles ====================

    static class FakeUrlClickRepository implements UrlClickRepository {
        private final List<UrlClick> storage = new ArrayList<>();
        private final List<UrlClick> individualSaves = new ArrayList<>();
        private int saveCallCount = 0;
        private int saveAllCallCount = 0;

        @Override
        public UrlClick save(UrlClick urlClick) {
            saveCallCount++;
            storage.add(urlClick);
            individualSaves.add(urlClick);
            return urlClick;
        }

        @Override
        public void saveAll(List<UrlClick> urlClicks) {
            saveAllCallCount++;
            storage.addAll(urlClicks);
        }

        @Override
        public long countByShortCode(String shortCode) {
            return 0;
        }

        @Override
        public List<UrlClick> findByShortCode(String shortCode) {
            return List.of();
        }

        @Override
        public List<UrlClick> findByShortCodeWithLimit(String shortCode, int limit) {
            return List.of();
        }

        @Override
        public List<UrlClick> findByShortCodeAndClickedAtBetween(
                String shortCode,
                LocalDateTime start,
                LocalDateTime end
        ) {
            return List.of();
        }

        List<UrlClick> getSavedClicks() {
            return storage;
        }

        List<UrlClick> getIndividualSavedClicks() {
            return individualSaves;
        }

        int getSaveCallCount() {
            return saveCallCount;
        }

        int getSaveAllCallCount() {
            return saveAllCallCount;
        }
    }

    static class FailingOnceRepository extends FakeUrlClickRepository {
        @Override
        public void saveAll(List<UrlClick> urlClicks) {
            super.saveAll(urlClicks);
            throw new RuntimeException("Bulk insert failed");
        }
    }

    static class AlwaysFailingRepository extends FakeUrlClickRepository {
        @Override
        public UrlClick save(UrlClick urlClick) {
            super.save(urlClick);
            throw new RuntimeException("Save failed");
        }

        @Override
        public void saveAll(List<UrlClick> urlClicks) {
            super.saveAll(urlClicks);
            throw new RuntimeException("Bulk insert failed");
        }
    }

    static class FailingTwiceRepository extends FakeUrlClickRepository {
        private int individualSaveCount = 0;

        @Override
        public UrlClick save(UrlClick urlClick) {
            super.save(urlClick);
            individualSaveCount++;

            if (individualSaveCount <= 2) {
                throw new RuntimeException("Save failed");
            }

            return urlClick;
        }

        @Override
        public void saveAll(List<UrlClick> urlClicks) {
            super.saveAll(urlClicks);
            throw new RuntimeException("Bulk insert failed");
        }
    }

    static class SelectiveFailingRepository extends FakeUrlClickRepository {
        private final String failShortCode;

        SelectiveFailingRepository(String failShortCode) {
            this.failShortCode = failShortCode;
        }

        @Override
        public UrlClick save(UrlClick urlClick) {
            super.save(urlClick);

            if (urlClick.getShortCode().equals(failShortCode)) {
                throw new RuntimeException("Save failed for " + failShortCode);
            }

            return urlClick;
        }

        @Override
        public void saveAll(List<UrlClick> urlClicks) {
            super.saveAll(urlClicks);
            throw new RuntimeException("Bulk insert failed");
        }
    }

    static class SpyClickEventDLQPublisher implements ClickEventDLQPublisher {
        private final List<DLQEventRecord> dlqEvents = new ArrayList<>();

        @Override
        public void publishToDLQ(UrlClickedEvent event, Exception exception, int retryCount) {
            dlqEvents.add(new DLQEventRecord(event, exception, retryCount));
        }

        List<DLQEventRecord> getDlqEvents() {
            return dlqEvents;
        }

        record DLQEventRecord(UrlClickedEvent event, Exception exception, int retryCount) {}
    }
}
