package com.io.bitly.api;

import static com.io.bitly.api.dto.ShortenRequest.*;

import com.io.bitly.api.dto.ShortenResponse.CreateShortUrlResponse;
import com.io.bitly.application.ShortUrlService;
import com.io.bitly.application.dto.ShortenUrlCommand.CreateShortUrlCommand;
import com.io.bitly.application.dto.ShortenUrlCommand.ShortUrlLookupCommand;
import com.io.bitly.application.dto.ShortenUrlResult.CreateShortUrlResult;
import com.io.bitly.application.dto.ShortenUrlResult.ShortUrlLookupResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@Slf4j
@RestController
@Profile("!phase4")
@RequiredArgsConstructor
@RequestMapping("/api/v1/urls")
public class ShortUrlController {

    private final ShortUrlService shortUrlService;

    @PostMapping("/shorten")
    public ResponseEntity<CreateShortUrlResponse> shortenUrl(@Valid @RequestBody CreateShortUrlRequest request) {
        log.info("Shorten URL request: {}", request.originalUrl());

        CreateShortUrlResult result = shortUrlService.shortenUrl(CreateShortUrlCommand.of(request.originalUrl()));
        return ResponseEntity.ok(CreateShortUrlResponse.of(result.shortCode()));
    }

    @GetMapping("/{shortCode}")
    public ResponseEntity<Void> redirect(@PathVariable String shortCode) {
        log.info("Redirect request for short code: {}", shortCode);

        ShortUrlLookupResult result = shortUrlService.findOriginalUrl(ShortUrlLookupCommand.of(shortCode));

        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(result.originalUrl()))
                .build();
    }
}
