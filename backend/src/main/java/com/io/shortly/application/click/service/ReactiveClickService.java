package com.io.shortly.application.click.service;

import com.io.shortly.domain.click.ReactiveUrlClickService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Profile("phase5")
@Service
@RequiredArgsConstructor
public class ReactiveClickService {

    private final ReactiveUrlClickService reactiveUrlClickService;

    public Mono<Void> incrementClickCount(Long urlId) {
        return reactiveUrlClickService.incrementClickCount(urlId);
    }
}

