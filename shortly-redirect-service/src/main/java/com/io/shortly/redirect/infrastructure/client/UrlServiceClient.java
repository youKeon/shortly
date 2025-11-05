package com.io.shortly.redirect.infrastructure.client;

import com.io.shortly.redirect.domain.Redirect;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class UrlServiceClient {

    private final RestClient urlServiceRestClient;

    private static final String GET_SHORT_CODE_URI = "/api/v1/urls/{shortCode}";

    public Optional<Redirect> findByShortCode(String shortCode) {
        log.info("[API Fallback] URL Service 호출 시작: shortCode={}", shortCode);

        try {

            UrlLookupResponse response = urlServiceRestClient.get()
                .uri(GET_SHORT_CODE_URI, shortCode)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (request, responseEntity) -> log.warn(
                    "[API Fallback] 4xx 에러: status={}, shortCode={}",
                    responseEntity.getStatusCode(), shortCode)
                )
                .onStatus(HttpStatusCode::is5xxServerError, (request, responseEntity) -> log.error(
                    "[API Fallback] 5xx 에러: status={}, shortCode={}",
                    responseEntity.getStatusCode(), shortCode)
                )
                .body(UrlLookupResponse.class);

            if (response == null) {
                log.warn("[API Fallback] 응답이 null: shortCode={}", shortCode);
                return Optional.empty();
            }

            log.info("[API Fallback] 조회 성공: shortCode={}, url={}",
                response.shortCode(), response.originalUrl());

            return Optional.of(Redirect.create(
                response.shortCode(),
                response.originalUrl()
            ));

        } catch (RestClientException e) {
            log.error("[API Fallback] 호출 실패: shortCode={}, error={}",
                shortCode, e.getMessage());
            return Optional.empty();
        } catch (Exception e) {
            log.error("[API Fallback] 예상치 못한 오류: shortCode={}", shortCode, e);
            return Optional.empty();
        }
    }

    public record UrlLookupResponse(
        String shortCode,
        String originalUrl
    ) {

    }
}
