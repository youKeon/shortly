package com.io.shortly.url.api;

import static com.io.shortly.url.api.dto.ShortUrlRequest.ShortenRequest;
import static com.io.shortly.url.application.dto.ShortUrlCommand.ShortenCommand;
import static com.io.shortly.url.application.dto.ShortUrlResult.ShortenedResult;

import com.io.shortly.url.api.dto.ShortUrlResponse.ShortenedResponse;
import com.io.shortly.url.application.UrlService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/urls")
public class UrlController {

    private final UrlService urlService;

    @PostMapping("/shorten")
    @ResponseStatus(HttpStatus.CREATED)
    public ShortenedResponse shortenUrl(@Valid @RequestBody ShortenRequest request) {
        ShortenedResult result = urlService.shortenUrl(
            ShortenCommand.of(request.originalUrl())
        );
        log.info("URL shortened: {}", result.shortCode());
        return new ShortenedResponse(result.shortCode(), result.originalUrl());
    }
}
