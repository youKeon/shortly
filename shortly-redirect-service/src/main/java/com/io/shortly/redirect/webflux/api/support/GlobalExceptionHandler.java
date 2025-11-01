package com.io.shortly.redirect.webflux.api.support;

import com.io.shortly.shared.exception.BaseReactiveExceptionHandler;
import com.io.shortly.shared.exception.ErrorResponse;
import com.io.shortly.redirect.webflux.domain.ShortCodeNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@RestControllerAdvice
public class GlobalExceptionHandler extends BaseReactiveExceptionHandler {

    @Value("${spring.application.name:redirect-service}")
    private String serviceName;

    @ExceptionHandler(ShortCodeNotFoundException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleShortCodeNotFoundException(
            ShortCodeNotFoundException ex,
            ServerWebExchange exchange
    ) {
        String traceId = getTraceId();
        logWarn(ex, exchange, traceId);

        ErrorResponse errorResponse = ErrorResponse.of(
                ex.getErrorCode(),
                ex.getMessage(),
                exchange.getRequest().getPath().value(),
                serviceName,
                traceId
        );

        return Mono.just(ResponseEntity
                .status(ex.getErrorCode().getHttpStatus())
                .body(errorResponse));
    }
}
