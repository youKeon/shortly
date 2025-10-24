package com.io.shortly.shared.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        String traceId,
        String serviceId,
        String code,
        String message,
        LocalDateTime timestamp,
        String path,
        List<ValidationError> errors
) {

    public static ErrorResponse of(ErrorCode errorCode, String path, String serviceId, String traceId) {
        return new ErrorResponse(
                traceId,
                serviceId,
                errorCode.getCode(),
                errorCode.getMessage(),
                LocalDateTime.now(),
                path,
                null
        );
    }

    public static ErrorResponse of(ErrorCode errorCode, String message, String path, String serviceId, String traceId) {
        return new ErrorResponse(
                traceId,
                serviceId,
                errorCode.getCode(),
                message,
                LocalDateTime.now(),
                path,
                null
        );
    }

    public static ErrorResponse withValidationErrors(
            ErrorCode errorCode,
            String path,
            String serviceId,
            String traceId,
            List<ValidationError> errors
    ) {
        return new ErrorResponse(
                traceId,
                serviceId,
                errorCode.getCode(),
                errorCode.getMessage(),
                LocalDateTime.now(),
                path,
                errors
        );
    }

    public record ValidationError(
            String field,
            String rejectedValue,
            String message
    ) {
        public static ValidationError of(String field, String rejectedValue, String message) {
            return new ValidationError(field, rejectedValue, message);
        }
    }
}
