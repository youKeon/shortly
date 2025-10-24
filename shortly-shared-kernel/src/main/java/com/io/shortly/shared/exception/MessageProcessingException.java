package com.io.shortly.shared.exception;

import lombok.Getter;

@Getter
public class MessageProcessingException extends BusinessException {

    private final String topic;
    private final String key;
    private final String eventType;

    public MessageProcessingException(String topic, String key, String eventType, Throwable cause) {
        super(
            CommonErrorCode.MESSAGE_PROCESSING_FAILED,
            String.format("Failed to process message: topic=%s, key=%s, eventType=%s", topic, key, eventType),
            cause
        );
        this.topic = topic;
        this.key = key;
        this.eventType = eventType;
    }

    public MessageProcessingException(String topic, String key, String eventType, String message, Throwable cause) {
        super(
            CommonErrorCode.MESSAGE_PROCESSING_FAILED,
            message,
            cause
        );
        this.topic = topic;
        this.key = key;
        this.eventType = eventType;
    }
}
