package com.io.shortly.click.domain;

public class InvalidShortCodeException extends RuntimeException {

    public InvalidShortCodeException(String message) {
        super(message);
    }

    public InvalidShortCodeException(String message, Throwable cause) {
        super(message, cause);
    }
}
