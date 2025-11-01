package com.io.shortly.redirect.mvc.api.support;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    private String code;
    private String message;
    private LocalDateTime timestamp;
    private String path;

    public static ErrorResponse of(
        RedirectErrorCode errorCode,
        String path
    ) {
        return new ErrorResponse(
            errorCode.getCode(),
            errorCode.getMessage(),
            LocalDateTime.now(),
            path
        );
    }

    public static ErrorResponse of(
        String code,
        String message,
        String path
    ) {
        return new ErrorResponse(
            code,
            message,
            LocalDateTime.now(),
            path
        );
    }
}
