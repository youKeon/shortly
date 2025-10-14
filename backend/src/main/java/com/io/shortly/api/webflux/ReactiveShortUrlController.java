package com.io.shortly.api.webflux;

import com.io.shortly.api.dto.ShortenRequest.CreateShortUrlRequest;
import com.io.shortly.api.dto.ShortenResponse.CreateShortUrlResponse;
import com.io.shortly.application.shorturl.facade.ReactiveShortUrlFacade;
import com.io.shortly.application.click.service.ReactiveUrlClickService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;

@Profile("phase5")
@RestController
@RequestMapping("/api/v1/urls")
@RequiredArgsConstructor
public class ReactiveShortUrlController {
    private final ReactiveShortUrlFacade shortUrlFacade;
    private final ReactiveUrlClickService urlClickService;

    @PostMapping("/shorten")
    public Mono<CreateShortUrlResponse> shortenUrl(
        @Valid @RequestBody CreateShortUrlRequest request
    ) {
        return shortUrlFacade.shortenUrl(request.originalUrl())
                .map(result -> new CreateShortUrlResponse(result.shortCode()));
    }

    @GetMapping("/{shortCode}")
    public Mono<Void> redirect(@PathVariable String shortCode, ServerWebExchange exchange) {
        return shortUrlFacade.getOriginalUrl(shortCode)
                .flatMap(result -> {
                    if (result.urlId() != null) {
                        urlClickService.incrementClickCount(result.urlId()).subscribe();
                    }

                    // 리디렉트 응답
                    exchange.getResponse().setStatusCode(HttpStatus.FOUND);
                    exchange.getResponse().getHeaders().setLocation(URI.create(result.originalUrl()));
                    return exchange.getResponse().setComplete();
                });
    }
}


