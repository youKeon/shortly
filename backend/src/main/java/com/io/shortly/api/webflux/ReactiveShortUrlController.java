package com.io.shortly.api.webflux;

import com.io.shortly.api.dto.ShortenRequest.CreateShortUrlRequest;
import com.io.shortly.api.dto.ShortenResponse.CreateShortUrlResponse;
import com.io.shortly.application.facade.ReactiveShortUrlFacade;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Profile("phase5")
@RestController
@RequestMapping("/api/v1/urls")
@RequiredArgsConstructor
public class ReactiveShortUrlController {
    private final ReactiveShortUrlFacade shortUrlFacade;

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
                .flatMap(result -> Mono.defer(() -> {
                    exchange.getResponse().setStatusCode(HttpStatus.FOUND);
                    exchange.getResponse().getHeaders().setLocation(URI.create(result.originalUrl()));
                    return exchange.getResponse().setComplete();
                }));
    }
}
