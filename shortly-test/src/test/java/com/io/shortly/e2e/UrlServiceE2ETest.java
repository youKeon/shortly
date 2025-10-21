package com.io.shortly.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.io.shortly.url.UrlServiceApplication;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * URL Service E2E 테스트
 *
 * TestContainers를 사용하여 실제 MySQL 환경에서 URL 서비스를 테스트합니다.
 */
@Testcontainers
@SpringBootTest(
    classes = UrlServiceApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "spring.profiles.active=test"
    }
)
@EmbeddedKafka(
    partitions = 1,
    topics = {"url-created"},
    brokerProperties = {
        "listeners=PLAINTEXT://localhost:9092",
        "port=9092"
    }
)
@DisplayName("URL Service E2E 테스트")
class UrlServiceE2ETest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("shortly_url_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:9092");
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    @DisplayName("URL 단축 API 호출 성공")
    void shortenUrl_Success() {
        // Given
        String originalUrl = "https://example.com/very/long/url/for/testing";
        Map<String, String> request = Map.of("originalUrl", originalUrl);

        // When
        ResponseEntity<Map> response = restTemplate.postForEntity(
            "/api/v1/urls/shorten",
            request,
            Map.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).containsKey("shortCode");
        assertThat(response.getBody()).containsKey("originalUrl");

        String shortCode = (String) response.getBody().get("shortCode");
        assertThat(shortCode).isNotNull();
        assertThat(shortCode).hasSize(6);
        assertThat(response.getBody().get("originalUrl")).isEqualTo(originalUrl);
    }

    @Test
    @DisplayName("여러 URL 단축 시 각각 다른 코드 생성")
    void shortenMultipleUrls_GeneratesDifferentCodes() {
        // Given
        String url1 = "https://example.com/url1";
        String url2 = "https://example.com/url2";
        String url3 = "https://example.com/url3";

        // When
        ResponseEntity<Map> response1 = restTemplate.postForEntity(
            "/api/v1/urls/shorten",
            Map.of("originalUrl", url1),
            Map.class
        );
        ResponseEntity<Map> response2 = restTemplate.postForEntity(
            "/api/v1/urls/shorten",
            Map.of("originalUrl", url2),
            Map.class
        );
        ResponseEntity<Map> response3 = restTemplate.postForEntity(
            "/api/v1/urls/shorten",
            Map.of("originalUrl", url3),
            Map.class
        );

        // Then
        String code1 = (String) response1.getBody().get("shortCode");
        String code2 = (String) response2.getBody().get("shortCode");
        String code3 = (String) response3.getBody().get("shortCode");

        assertThat(code1).isNotEqualTo(code2);
        assertThat(code2).isNotEqualTo(code3);
        assertThat(code1).isNotEqualTo(code3);
    }

    @Test
    @DisplayName("동일 URL 여러 번 단축 시 매번 다른 코드 생성")
    void shortenSameUrl_MultipleTimes_GeneratesDifferentCodes() {
        // Given
        String originalUrl = "https://example.com/same/url";

        // When - 동일 URL 3번 단축
        ResponseEntity<Map> response1 = restTemplate.postForEntity(
            "/api/v1/urls/shorten",
            Map.of("originalUrl", originalUrl),
            Map.class
        );
        ResponseEntity<Map> response2 = restTemplate.postForEntity(
            "/api/v1/urls/shorten",
            Map.of("originalUrl", originalUrl),
            Map.class
        );
        ResponseEntity<Map> response3 = restTemplate.postForEntity(
            "/api/v1/urls/shorten",
            Map.of("originalUrl", originalUrl),
            Map.class
        );

        // Then - 각각 다른 코드 생성
        String code1 = (String) response1.getBody().get("shortCode");
        String code2 = (String) response2.getBody().get("shortCode");
        String code3 = (String) response3.getBody().get("shortCode");

        assertThat(code1).isNotEqualTo(code2);
        assertThat(code2).isNotEqualTo(code3);
        assertThat(code1).isNotEqualTo(code3);

        // 모두 동일한 원본 URL
        assertThat(response1.getBody().get("originalUrl")).isEqualTo(originalUrl);
        assertThat(response2.getBody().get("originalUrl")).isEqualTo(originalUrl);
        assertThat(response3.getBody().get("originalUrl")).isEqualTo(originalUrl);
    }

    @Test
    @DisplayName("빈 URL로 단축 요청 시 400 에러")
    void shortenUrl_EmptyUrl_Returns400() {
        // Given
        Map<String, String> request = Map.of("originalUrl", "");

        // When
        ResponseEntity<Map> response = restTemplate.postForEntity(
            "/api/v1/urls/shorten",
            request,
            Map.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("null URL로 단축 요청 시 400 에러")
    void shortenUrl_NullUrl_Returns400() {
        // Given
        Map<String, Object> request = Map.of();

        // When
        ResponseEntity<Map> response = restTemplate.postForEntity(
            "/api/v1/urls/shorten",
            request,
            Map.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Base62 문자만 포함된 6자 코드 생성")
    void shortenUrl_GeneratesValidBase62Code() {
        // Given
        String originalUrl = "https://example.com/base62/test";

        // When
        ResponseEntity<Map> response = restTemplate.postForEntity(
            "/api/v1/urls/shorten",
            Map.of("originalUrl", originalUrl),
            Map.class
        );

        // Then
        String shortCode = (String) response.getBody().get("shortCode");
        assertThat(shortCode).hasSize(6);
        assertThat(shortCode).matches("[0-9A-Za-z]{6}");  // Base62: 0-9, A-Z, a-z
    }

    @Test
    @DisplayName("대량 URL 단축 시 성능 테스트")
    void shortenUrl_BulkTest() {
        // Given
        int bulkSize = 100;
        int successCount = 0;

        // When
        long startTime = System.currentTimeMillis();
        for (int i = 0; i < bulkSize; i++) {
            String url = String.format("https://example.com/bulk/test/%d", i);
            ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/v1/urls/shorten",
                Map.of("originalUrl", url),
                Map.class
            );
            if (response.getStatusCode() == HttpStatus.OK) {
                successCount++;
            }
        }
        long endTime = System.currentTimeMillis();

        // Then
        assertThat(successCount).isEqualTo(bulkSize);
        long duration = endTime - startTime;
        System.out.println("100개 URL 단축 소요 시간: " + duration + "ms");
        System.out.println("평균 처리 시간: " + (duration / bulkSize) + "ms");
    }
}
