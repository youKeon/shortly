package com.io.shortly.redirect.webflux.api;

import static com.io.shortly.redirect.webflux.api.dto.RedirectRequest.GetRedirectRequest;
import static com.io.shortly.redirect.webflux.api.dto.RedirectRequest.SHORT_CODE_REGEX;

import com.io.shortly.redirect.webflux.application.RedirectService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@Slf4j
@RestController
@RequiredArgsConstructor
@Validated
public class RedirectController {

    private final RedirectService redirectService;

    @GetMapping("/r/{shortCode}")
    public Mono<ResponseEntity<Void>> redirect(
            @PathVariable
            @NotBlank(message = "Short code must not be blank")
            @Pattern(
                    regexp = SHORT_CODE_REGEX,
                    message = "Short code must be 6-10 alphanumeric characters"
            )
            String shortCode
    ) {
        GetRedirectRequest request = GetRedirectRequest.of(shortCode);
        return redirectService.getOriginalUrl(request.shortCode())
            .map(result -> ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(result.originalUrl()))
                .build());
    }
}
