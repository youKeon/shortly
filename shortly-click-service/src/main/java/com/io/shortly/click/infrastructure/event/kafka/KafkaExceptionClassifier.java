package com.io.shortly.click.infrastructure.event.kafka;

import java.net.SocketTimeoutException;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.SQLTransientException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class KafkaExceptionClassifier {

    public FailureType classify(Throwable exception) {
        if (exception == null) {
            return FailureType.PERMANENT;
        }

        // 1. 중복 이벤트
        if (isDuplicateKeyException(exception)) {
            log.debug("[Exception Classifier] 중복 이벤트 감지 - {}", exception.getClass().getSimpleName());
            return FailureType.DUPLICATE;
        }

        // 2. 영구적 실패 (재시도 X)
        if (isPermanentFailure(exception)) {
            log.warn("[Exception Classifier] 영구적 실패 감지 - {}: {}",
                    exception.getClass().getSimpleName(), exception.getMessage());
            return FailureType.PERMANENT;
        }

        // 3. 일시적 실패 (재시도 O)
        if (isTransientFailure(exception)) {
            log.info("[Exception Classifier] 일시적 실패 감지 - {}: {}",
                    exception.getClass().getSimpleName(), exception.getMessage());
            return FailureType.TRANSIENT;
        }

        // 4. 기본값: 일시적 실패로 간주
        log.warn("[Exception Classifier] 알 수 없는 예외, 일시적 실패로 간주 - {}",
                exception.getClass().getSimpleName());
        return FailureType.TRANSIENT;
    }

    /**
     * 중복 키 예외 판별
     */
    private boolean isDuplicateKeyException(Throwable exception) {
        // 유니크 제약 위반
        if (exception instanceof DataIntegrityViolationException) {
            Throwable rootCause = getRootCause(exception);

            if (rootCause instanceof SQLIntegrityConstraintViolationException) {
                String message = rootCause.getMessage();
                return message != null && message.contains("uk_event_id");
            }
        }

        return false;
    }

    /**
     * 영구적 실패 판별
     */
    private boolean isPermanentFailure(Throwable exception) {
        // 1. 데이터 형식 오류
        if (exception instanceof DeserializationException) {
            return true;
        }

        // 2. 비즈니스 규칙 위반
        if (exception instanceof IllegalArgumentException) {
            return true; // 잘못된 입력값
        }

        // 3. 필수 필드 누락
        if (exception instanceof NullPointerException) {
            return true; // 필수 데이터 누락
        }

        // 4. DataIntegrityViolationException (중복 제외)
        if (exception instanceof DataIntegrityViolationException) {
            // 중복이 아닌 데이터 무결성 오류 (FK 위반, NOT NULL 위반 등)
            if (!isDuplicateKeyException(exception)) {
                return true;
            }
        }

        // 5. ClassCastException - 타입 변환 오류
        return exception instanceof ClassCastException;
    }

    /**
     * 일시적 실패 판별
     */
    private boolean isTransientFailure(Throwable exception) {
        // 1. Spring의 TransientDataAccessException
        if (exception instanceof TransientDataAccessException) {
            return true;
        }

        // 2. 데이터베이스 연결 실패
        if (exception instanceof DataAccessResourceFailureException) {
            return true;
        }

        // 3. SQL 일시적 예외
        Throwable rootCause = getRootCause(exception);
        if (rootCause instanceof SQLTransientException) {
            return true;
        }

        // 4. 네트워크 타임아웃
        if (rootCause instanceof SocketTimeoutException) {
            return true;
        }

        // 5. SQLException의 일시적 오류 판별
        if (rootCause instanceof SQLException sqlException) {
            String sqlState = sqlException.getSQLState();
            return sqlState != null && (sqlState.startsWith("08") || sqlState.startsWith("40"));
        }

        return false;
    }

    /**
     * Root Cause 추출
     */
    private Throwable getRootCause(Throwable exception) {
        Throwable rootCause = exception;
        while (rootCause.getCause() != null && rootCause.getCause() != rootCause) {
            rootCause = rootCause.getCause();
        }
        return rootCause;
    }

    /**
     * 예외가 재시도 가능한지 판별
     */
    public boolean isRetryable(Throwable exception) {
        FailureType failureType = classify(exception);
        return failureType.isRetryable();
    }
}
