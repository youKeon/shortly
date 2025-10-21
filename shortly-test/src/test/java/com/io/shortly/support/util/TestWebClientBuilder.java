package com.io.shortly.support.util;

import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.client.ExchangeStrategies;

import java.time.Duration;

public class TestWebClientBuilder {

    private Duration timeout = Duration.ofSeconds(30);
    private int maxMemorySize = 10 * 1024 * 1024; // 10MB

    public static TestWebClientBuilder create() {
        return new TestWebClientBuilder();
    }

    public TestWebClientBuilder timeout(Duration timeout) {
        this.timeout = timeout;
        return this;
    }

    public TestWebClientBuilder maxMemorySize(int maxMemorySize) {
        this.maxMemorySize = maxMemorySize;
        return this;
    }

    public WebTestClient.Builder builder() {
        return WebTestClient.bindToServer()
                .responseTimeout(timeout)
                .exchangeStrategies(ExchangeStrategies.builder()
                        .codecs(configurer -> configurer
                                .defaultCodecs()
                                .maxInMemorySize(maxMemorySize))
                        .build());
    }

    public WebTestClient buildForBaseUrl(String baseUrl) {
        return builder()
                .baseUrl(baseUrl)
                .build();
    }
}
