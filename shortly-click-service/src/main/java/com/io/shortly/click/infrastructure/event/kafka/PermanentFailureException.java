package com.io.shortly.click.infrastructure.event.kafka;

public class PermanentFailureException extends RuntimeException {

    public PermanentFailureException(String message) {
        super(message);
    }

    public PermanentFailureException(String message, Throwable cause) {
        super(message, cause);
    }
}
