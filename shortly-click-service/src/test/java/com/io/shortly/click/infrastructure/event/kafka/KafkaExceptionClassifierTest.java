package com.io.shortly.click.infrastructure.event.kafka;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.kafka.support.serializer.DeserializationException;

import java.sql.SQLIntegrityConstraintViolationException;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaExceptionClassifierTest {

    private final KafkaExceptionClassifier classifier = new KafkaExceptionClassifier();

    @Test
    @DisplayName("중복 이벤트를 올바르게 분류한다")
    void classifyDuplicateKeyException() {
        // given
        SQLIntegrityConstraintViolationException sqlException =
            new SQLIntegrityConstraintViolationException("Duplicate entry '123' for key 'uk_event_id'");
        DataIntegrityViolationException exception =
            new DataIntegrityViolationException("Duplicate key", sqlException);

        // when
        FailureType failureType = classifier.classify(exception);

        // then
        assertThat(failureType).isEqualTo(FailureType.DUPLICATE);
        assertThat(failureType.isRetryable()).isFalse();
    }

    @Test
    @DisplayName("영구적 실패를 올바르게 분류한다 - JSON 파싱 실패")
    void classifyPermanentFailure_DeserializationException() {
        // given
        DeserializationException exception = new DeserializationException(
            "Failed to deserialize", new byte[0], false, null);

        // when
        FailureType failureType = classifier.classify(exception);

        // then
        assertThat(failureType).isEqualTo(FailureType.PERMANENT);
        assertThat(failureType.isRetryable()).isFalse();
    }

    @Test
    @DisplayName("영구적 실패를 올바르게 분류한다 - 비즈니스 규칙 위반")
    void classifyPermanentFailure_IllegalArgumentException() {
        // given
        IllegalArgumentException exception = new IllegalArgumentException("Invalid short code");

        // when
        FailureType failureType = classifier.classify(exception);

        // then
        assertThat(failureType).isEqualTo(FailureType.PERMANENT);
        assertThat(failureType.isRetryable()).isFalse();
    }

    @Test
    @DisplayName("영구적 실패를 올바르게 분류한다 - 필수 필드 누락")
    void classifyPermanentFailure_NullPointerException() {
        // given
        NullPointerException exception = new NullPointerException("Required field is null");

        // when
        FailureType failureType = classifier.classify(exception);

        // then
        assertThat(failureType).isEqualTo(FailureType.PERMANENT);
        assertThat(failureType.isRetryable()).isFalse();
    }

    @Test
    @DisplayName("일시적 실패를 올바르게 분류한다 - DB 연결 실패")
    void classifyTransientFailure_DataAccessResourceFailureException() {
        // given
        DataAccessResourceFailureException exception =
            new DataAccessResourceFailureException("Connection failed");

        // when
        FailureType failureType = classifier.classify(exception);

        // then
        assertThat(failureType).isEqualTo(FailureType.TRANSIENT);
        assertThat(failureType.isRetryable()).isTrue();
    }

    @Test
    @DisplayName("일시적 실패를 올바르게 분류한다 - 쿼리 타임아웃")
    void classifyTransientFailure_QueryTimeoutException() {
        // given
        QueryTimeoutException exception = new QueryTimeoutException("Query timeout");

        // when
        FailureType failureType = classifier.classify(exception);

        // then
        assertThat(failureType).isEqualTo(FailureType.TRANSIENT);
        assertThat(failureType.isRetryable()).isTrue();
    }

    @Test
    @DisplayName("알 수 없는 예외는 일시적 실패로 분류한다")
    void classifyUnknownException_AsTransient() {
        // given
        RuntimeException exception = new RuntimeException("Unknown error");

        // when
        FailureType failureType = classifier.classify(exception);

        // then
        assertThat(failureType).isEqualTo(FailureType.TRANSIENT);
        assertThat(failureType.isRetryable()).isTrue();
    }

    @Test
    @DisplayName("isRetryable 메서드가 올바르게 동작한다")
    void isRetryable() {
        // given
        Exception duplicateException = new DataIntegrityViolationException(
            "Duplicate",
            new SQLIntegrityConstraintViolationException("Duplicate entry for key 'uk_event_id'")
        );
        Exception permanentException = new IllegalArgumentException("Invalid");
        Exception transientException = new QueryTimeoutException("Timeout");

        // when & then
        assertThat(classifier.isRetryable(duplicateException)).isFalse();
        assertThat(classifier.isRetryable(permanentException)).isFalse();
        assertThat(classifier.isRetryable(transientException)).isTrue();
    }
}
