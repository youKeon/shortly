package com.io.shortly.api.mvc;

import static com.io.shortly.api.dto.ShortenRequest.*;

import com.io.shortly.api.dto.ShortenResponse.CreateShortUrlResponse;
import com.io.shortly.application.shorturl.facade.ShortUrlFacade;
import com.io.shortly.application.shorturl.command.ShortenUrlCommand.CreateShortUrlCommand;
import com.io.shortly.application.shorturl.command.ShortenUrlCommand.ShortUrlLookupCommand;
import com.io.shortly.application.shorturl.result.ShortenUrlResult.CreateShortUrlResult;
import com.io.shortly.application.shorturl.result.ShortenUrlResult.ShortUrlLookupResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/urls")
@org.springframework.context.annotation.Profile("!phase5")
public class ShortUrlController {

    private final ShortUrlFacade shortUrlFacade;

    @PostMapping("/shorten")
    public ResponseEntity<CreateShortUrlResponse> shortenUrl(
        @Valid @RequestBody CreateShortUrlRequest request
    ) {
        CreateShortUrlResult result = shortUrlFacade.shortenUrl(
            CreateShortUrlCommand.of(request.originalUrl())
        );
        return ResponseEntity.ok(
            CreateShortUrlResponse.of(result.shortCode())
        );
    }

    @GetMapping("/{shortCode}")
    public ResponseEntity<Void> redirect(@PathVariable String shortCode) {
        ShortUrlLookupResult result = shortUrlFacade.findOriginalUrl(
            ShortUrlLookupCommand.of(shortCode)
        );

        return ResponseEntity.status(HttpStatus.FOUND)
            .location(URI.create(result.originalUrl()))
            .build();
    }
}

