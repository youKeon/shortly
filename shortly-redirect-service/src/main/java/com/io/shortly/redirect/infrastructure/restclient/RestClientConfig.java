package com.io.shortly.redirect.infrastructure.restclient;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * RestClient 설정
 *
 * <p>URL Service와의 HTTP 통신을 위한 RestClient를 구성합니다.
 * <p>타임아웃 설정을 통해 Cascading Failure를 방지합니다.
 *
 * <h3>타임아웃 설정</h3>
 * <ul>
 *   <li>연결 타임아웃 (connect-timeout): 1초 - URL Service와의 TCP 연결 수립 시간</li>
 *   <li>읽기 타임아웃 (read-timeout): 2초 - URL Service 응답 대기 시간</li>
 * </ul>
 *
 * <h3>장애 시나리오</h3>
 * <p>URL Service 장애 시 최대 2초 내 실패 처리하여 Redirect Service 스레드 고갈 방지</p>
 */
@Configuration
public class RestClientConfig {

    @Bean
    public RestClient urlServiceRestClient(
            @Value("${shortly.url-service.base-url:http://localhost:8081}") String baseUrl,
            @Value("${shortly.url-service.connect-timeout:1000}") int connectTimeout,
            @Value("${shortly.url-service.read-timeout:2000}") int readTimeout
    ) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(connectTimeout));
        factory.setReadTimeout(Duration.ofMillis(readTimeout));

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }
}
