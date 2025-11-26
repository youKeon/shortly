package com.io.shortly.shared.api.support.error;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        String serviceId,
        String code,
        String message,
        LocalDateTime timestamp,
        String path) {

    public static ErrorResponse of(ErrorCode errorCode, String path, String serviceId) {
        return new ErrorResponse(
                serviceId,
                errorCode.getCode(),
                errorCode.getMessage(),
                LocalDateTime.now(),
                path);
    }

    public static ErrorResponse of(ErrorCode errorCode, String message, String path, String serviceId) {
        return new ErrorResponse(
                serviceId,
                errorCode.getCode(),
                message,
                LocalDateTime.now(),
                path);
    }
}
