package com.io.bitly.api;

import com.io.bitly.api.dto.ShortenRequest.CreateShortUrlRequest;
import com.io.bitly.api.dto.ShortenResponse.CreateShortUrlResponse;
import com.io.bitly.application.ReactiveShortUrlService;
import com.io.bitly.application.dto.ShortenUrlCommand.CreateShortUrlCommand;
import com.io.bitly.application.dto.ShortenUrlCommand.ShortUrlLookupCommand;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.net.URI;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/urls")
@Profile("phase4")
public class ReactiveShortUrlController {

    private final ReactiveShortUrlService shortUrlService;

    @PostMapping("/shorten")
    public Mono<ResponseEntity<CreateShortUrlResponse>> shortenUrl(
            @Valid @RequestBody CreateShortUrlRequest request) {

        log.debug("Shorten URL request: {}", request.originalUrl());

        return shortUrlService.shortenUrl(CreateShortUrlCommand.of(request.originalUrl()))
                .map(result -> ResponseEntity.ok(CreateShortUrlResponse.of(result.shortCode())))
                .doOnSuccess(response -> log.debug("Shorten URL success: {}",
                        response.getBody().shortCode()))
                .doOnError(error -> log.error("Shorten URL error: {}", error.getMessage()));
    }

    @GetMapping("/{shortCode}")
    public Mono<ResponseEntity<Void>> redirect(@PathVariable String shortCode) {

        log.debug("Redirect request for short code: {}", shortCode);

        return shortUrlService.findOriginalUrl(ShortUrlLookupCommand.of(shortCode))
                .map(result -> ResponseEntity.status(HttpStatus.FOUND)
                        .location(URI.create(result.originalUrl()))
                        .<Void>build())
                .doOnSuccess(response -> log.debug("Redirect success: {}",
                        response.getHeaders().getLocation()))
                .onErrorResume(error -> {
                    log.error("Redirect error for {}: {}", shortCode, error.getMessage());
                    return Mono.just(ResponseEntity.notFound().build());
                });
    }
}

