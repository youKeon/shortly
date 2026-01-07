package com.io.shortly.shared.event;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum TopicType {

    // Main Topics
    URL_CREATED("url-created"),
    URL_CLICKED("url-clicked"),

    // Dead Letter Queue Topics
    URL_CLICKED_DLQ("url-clicked-dlq"),  // Deprecated: 호환성 유지용
    URL_CLICKED_DLQ_PERMANENT("url-clicked-dlq-permanent"),  // 영구적 실패 (수동 처리)
    URL_CLICKED_DLQ_TRANSIENT("url-clicked-dlq-transient");  // 일시적 실패 (자동 재처리)

    private final String topicName;

    public String toString() {
        return topicName;
    }

    public static final class ConsumerGroups {
        public static final String CLICK_SERVICE = "click-service-group";

        private ConsumerGroups() {
            throw new AssertionError("Cannot instantiate constants class");
        }
    }
}
