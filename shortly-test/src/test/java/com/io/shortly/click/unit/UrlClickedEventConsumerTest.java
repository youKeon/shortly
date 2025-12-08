package com.io.shortly.click.unit;

import com.io.shortly.click.domain.UrlClick;
import com.io.shortly.click.domain.UrlClickRepository;
import com.io.shortly.click.infrastructure.event.kafka.UrlClickedEventConsumer;
import com.io.shortly.shared.event.EventType;
import com.io.shortly.shared.event.UrlClickedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

@DisplayName("UrlClickedEventConsumer 단위 테스트 - At-Least-Once 배치 처리")
class UrlClickedEventConsumerTest {

    @Nested
    @DisplayName("배치 이벤트 처리")
    class ConsumeBatchTest {

        @Test
        @DisplayName("배치 이벤트를 한 번에 처리한다")
        void consumeUrlClicked_BatchSuccess() {
            // given
            FakeUrlClickRepository repository = new FakeUrlClickRepository();
            UrlClickedEventConsumer consumer = new UrlClickedEventConsumer(repository);

            List<UrlClickedEvent> events = List.of(
                    UrlClickedEvent.of(1L, "code1", "url1"),
                    UrlClickedEvent.of(2L, "code2", "url2"),
                    UrlClickedEvent.of(3L, "code3", "url3"));

            // when
            consumer.consumeUrlClicked(events);

            // then
            assertThat(repository.getSavedClicks()).hasSize(3);
            assertThat(repository.getSaveAllCallCount()).isEqualTo(1); // 배치 처리로 1번 호출
        }

        @Test
        @DisplayName("빈 배치는 무시한다")
        void consumeUrlClicked_EmptyBatch() {
            // given
            FakeUrlClickRepository repository = new FakeUrlClickRepository();
            UrlClickedEventConsumer consumer = new UrlClickedEventConsumer(repository);

            // when
            consumer.consumeUrlClicked(List.of());

            // then
            assertThat(repository.getSavedClicks()).isEmpty();
            assertThat(repository.getSaveAllCallCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("이벤트를 UrlClick 도메인 객체로 변환하여 저장한다")
        void consumeUrlClicked_ConvertsToUrlClick() {
            // given
            FakeUrlClickRepository repository = new FakeUrlClickRepository();
            UrlClickedEventConsumer consumer = new UrlClickedEventConsumer(repository);

            List<UrlClickedEvent> events = List.of(UrlClickedEvent.of(1L, "code1", "url1"));

            // when
            consumer.consumeUrlClicked(events);

            // then
            assertThat(repository.getSavedClicks()).hasSize(1);
            UrlClick saved = repository.getSavedClicks().get(0);
            assertThat(saved.getEventId()).isEqualTo(1L);
            assertThat(saved.getShortCode()).isEqualTo("code1");
            assertThat(saved.getOriginalUrl()).isEqualTo("url1");
        }

        @Test
        @DisplayName("배치 저장 실패 시 개별 처리로 폴백한다")
        void consumeUrlClicked_FallbackOnBatchFailure() {
            // given
            FakeUrlClickRepository repository = new FakeUrlClickRepository();
            repository.enableBatchFailure(); // 배치 저장 실패 시뮬레이션
            UrlClickedEventConsumer consumer = new UrlClickedEventConsumer(repository);

            List<UrlClickedEvent> events = List.of(
                    UrlClickedEvent.of(1L, "code1", "url1"),
                    UrlClickedEvent.of(2L, "code2", "url2"),
                    UrlClickedEvent.of(3L, "code3", "url3"));

            // when
            consumer.consumeUrlClicked(events);

            // then
            assertThat(repository.getSavedClicks()).hasSize(3);
            assertThat(repository.getSaveAllCallCount()).isEqualTo(1); // 배치 시도
            assertThat(repository.getIndividualSaveCallCount()).isEqualTo(3); // 폴백으로 개별 저장
        }

        @Test
        @DisplayName("배치 내 중복 이벤트는 개별 처리에서 무시한다 (Idempotence)")
        void consumeUrlClicked_IgnoresDuplicateInBatch() {
            // given
            FakeUrlClickRepository repository = new FakeUrlClickRepository();
            repository.enableBatchFailure(); // 배치 저장 실패로 폴백 유도
            repository.enableDuplicateCheck(); // 중복 체크 활성화
            UrlClickedEventConsumer consumer = new UrlClickedEventConsumer(repository);

            // 중복된 eventId를 포함한 배치
            long duplicateEventId = 123L;
            List<UrlClickedEvent> events = List.of(
                    new UrlClickedEvent(duplicateEventId, EventType.URL_CLICKED, Instant.now(), "code1", "url1"),
                    UrlClickedEvent.of(2L, "code2", "url2"),
                    new UrlClickedEvent(duplicateEventId, EventType.URL_CLICKED, Instant.now(), "code1", "url1")); // 중복

            // when
            consumer.consumeUrlClicked(events);

            // then - 중복 제거되어 2개만 저장
            assertThat(repository.getSavedClicks()).hasSize(2);
            assertThat(repository.getIndividualSaveCallCount()).isEqualTo(3); // 3번 시도 (1번 중복으로 무시)
        }

        @Test
        @DisplayName("대용량 배치를 처리한다")
        void consumeUrlClicked_LargeBatch() {
            // given
            FakeUrlClickRepository repository = new FakeUrlClickRepository();
            UrlClickedEventConsumer consumer = new UrlClickedEventConsumer(repository);

            // 100개 이벤트 (max-poll-records: 100)
            List<UrlClickedEvent> events = new ArrayList<>();
            for (int i = 1; i <= 100; i++) {
                events.add(UrlClickedEvent.of((long) i, "code" + i, "url" + i));
            }

            // when
            consumer.consumeUrlClicked(events);

            // then
            assertThat(repository.getSavedClicks()).hasSize(100);
            assertThat(repository.getSaveAllCallCount()).isEqualTo(1); // 배치 처리
        }
    }

    static class FakeUrlClickRepository implements UrlClickRepository {
        private final List<UrlClick> storage = new ArrayList<>();
        private final Set<Long> eventIds = new HashSet<>();
        private boolean duplicateCheckEnabled = false;
        private boolean batchFailureEnabled = false;
        private int saveAllCallCount = 0;
        private int individualSaveCallCount = 0;

        void enableDuplicateCheck() {
            this.duplicateCheckEnabled = true;
        }

        void enableBatchFailure() {
            this.batchFailureEnabled = true;
        }

        @Override
        public UrlClick save(UrlClick urlClick) {
            individualSaveCallCount++;

            // 중복 체크가 활성화된 경우, eventId 중복 검사
            if (duplicateCheckEnabled && eventIds.contains(urlClick.getEventId())) {
                throw new DataIntegrityViolationException(
                        "Duplicate eventId: " + urlClick.getEventId());
            }

            storage.add(urlClick);
            eventIds.add(urlClick.getEventId());
            return urlClick;
        }

        @Override
        public void saveAll(List<UrlClick> urlClicks) {
            saveAllCallCount++;

            // 배치 실패 시뮬레이션
            if (batchFailureEnabled) {
                throw new DataIntegrityViolationException("Batch save failed");
            }

            for (UrlClick urlClick : urlClicks) {
                save(urlClick);
            }
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
                LocalDateTime end) {
            return List.of();
        }

        List<UrlClick> getSavedClicks() {
            return storage;
        }

        int getSaveAllCallCount() {
            return saveAllCallCount;
        }

        int getIndividualSaveCallCount() {
            return individualSaveCallCount;
        }
    }
}
