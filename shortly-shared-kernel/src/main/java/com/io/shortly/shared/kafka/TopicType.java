package com.io.shortly.shared.kafka;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum TopicType {

    // Main Topics
    URL_CREATED("url-created"),
    URL_CLICKED("url-clicked"),

    // Dead Letter Queue Topics
    URL_CLICKED_DLQ("url-clicked-dlq");

    private final String topicName;

    public String toString() {
        return topicName;
    }

    /**
     * Kafka Consumer Groups
     */
    public static final class ConsumerGroups {
        public static final String REDIRECT_SERVICE = "redirect-service-group";
        public static final String CLICK_SERVICE = "click-service-group";

        private ConsumerGroups() {
            throw new AssertionError("Cannot instantiate constants class");
        }
    }
}
