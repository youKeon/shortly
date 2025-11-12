package com.io.shortly.click.infrastructure.event.kafka;

import com.io.shortly.shared.event.UrlClickedEvent;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class ClickEventDLQRecord {

    /**
     * 원본 클릭 이벤트
     */
    private UrlClickedEvent originalEvent;

    /**
     * 에러 메시지
     */
    private String errorMessage;

    /**
     * 에러 타입 (예외 클래스명)
     */
    private String errorType;

    /**
     * 에러 스택 트레이스 (선택적, 최대 1000자)
     */
    private String stackTrace;

    /**
     * 실패 시각
     */
    private Instant failedAt;

    /**
     * 재시도 횟수
     */
    private int retryCount;

    /**
     * 원본 토픽명
     */
    private String originalTopic;

    /**
     * 실패한 Consumer 그룹
     */
    private String consumerGroup;

    public static ClickEventDLQRecord of(
            UrlClickedEvent originalEvent,
            Exception exception,
            int retryCount,
            String originalTopic,
            String consumerGroup
    ) {
        String stackTrace = getStackTraceAsString(exception);
        String truncatedStackTrace = stackTrace.length() > 1000
                ? stackTrace.substring(0, 1000) + "..."
                : stackTrace;

        return new ClickEventDLQRecord(
                originalEvent,
                exception.getMessage(),
                exception.getClass().getName(),
                truncatedStackTrace,
                Instant.now(),
                retryCount,
                originalTopic,
                consumerGroup
        );
    }

    private static String getStackTraceAsString(Exception exception) {
        StringBuilder sb = new StringBuilder();
        sb.append(exception.toString()).append("\n");

        for (StackTraceElement element : exception.getStackTrace()) {
            sb.append("\tat ").append(element.toString()).append("\n");
            if (sb.length() > 2000) break;  // 최대 2000자
        }

        return sb.toString();
    }
}
