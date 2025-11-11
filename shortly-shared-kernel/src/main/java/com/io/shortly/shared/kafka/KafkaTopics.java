package com.io.shortly.shared.kafka;

public final class KafkaTopics {

    // Main Topics
    public static final String URL_CREATED = "url-created";
    public static final String URL_CLICKED = "url-clicked";

    // Dead Letter Queue Topics
    public static final String URL_CLICKED_DLQ = "url-clicked-dlq";

    // Consumer Groups
    public static final String REDIRECT_SERVICE_GROUP = "redirect-service-group";
    public static final String CLICK_SERVICE_GROUP = "click-service-group";

    private KafkaTopics() {
        throw new AssertionError("Cannot instantiate constants class");
    }
}
