package com.io.shortly.shared.kafka;

public final class KafkaTopics {

    public static final String URL_CREATED = "url-created";
    public static final String URL_CLICKED = "url-clicked";

    public static final String REDIRECT_SERVICE_GROUP = "redirect-service-group";
    public static final String CLICK_SERVICE_GROUP = "click-service-group";

    private KafkaTopics() {
        throw new AssertionError("Cannot instantiate constants class");
    }
}
