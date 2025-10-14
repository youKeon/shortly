package com.io.shortly.api.mvc;

import static com.io.shortly.api.dto.ShortenRequest.CreateShortUrlRequest;

import com.io.shortly.api.dto.ShortenResponse.CreateShortUrlResponse;
import com.io.shortly.application.dto.ShortenUrlCommand.CreateShortUrlCommand;
import com.io.shortly.application.dto.ShortenUrlCommand.ShortUrlLookupCommand;
import com.io.shortly.application.dto.ShortenUrlResult.CreateShortUrlResult;
import com.io.shortly.application.dto.ShortenUrlResult.ShortUrlLookupResult;
import com.io.shortly.application.facade.ShortUrlFacade;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

