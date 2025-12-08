package com.io.shortly.redirect.integration;

import com.io.shortly.shared.event.EventType;
import com.io.shortly.shared.event.UrlClickedEvent;
import com.io.shortly.shared.kafka.TopicType;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Idempotence 설정 전후 비교 테스트
 *
 * Before: enable.idempotence=false, eventId unique constraint 없음
 * After: enable.idempotence=true, eventId unique constraint 있음
 */
@SpringBootTest(properties = {
                "spring.profiles.active=test",
                "spring.kafka.consumer.auto-offset-reset=earliest"
}, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
@DisplayName("Exactly-Once 멱등성 Before/After 비교 테스트")
class IdempotenceComparisonTest {

        @Container
        static KafkaContainer kafka = new KafkaContainer(
                        DockerImageName.parse("apache/kafka:3.7.0")
                                        .asCompatibleSubstituteFor("confluentinc/cp-kafka"));

        @Container
        static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
                        .withDatabaseName("shortly_click")
                        .withUsername("root")
                        .withPassword("password");

        @DynamicPropertySource
        static void overrideProperties(DynamicPropertyRegistry registry) {
                registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
                registry.add("spring.datasource.primary.jdbc-url", mysql::getJdbcUrl);
                registry.add("spring.datasource.primary.username", mysql::getUsername);
                registry.add("spring.datasource.primary.password", mysql::getPassword);
                registry.add("spring.datasource.replica.jdbc-url", mysql::getJdbcUrl);
                registry.add("spring.datasource.replica.username", mysql::getUsername);
                registry.add("spring.datasource.replica.password", mysql::getPassword);
        }

        @Autowired
        private JdbcTemplate jdbcTemplate;

        @BeforeEach
        void setUp() {
                // 테스트 전 데이터 초기화
                jdbcTemplate.execute("DELETE FROM url_clicks");
        }

        @AfterEach
        void tearDown() {
                jdbcTemplate.execute("DELETE FROM url_clicks");
        }

        private void removeUniqueConstraint() {
                try {
                        jdbcTemplate.execute("ALTER TABLE url_clicks DROP INDEX uk_event_id");
                        System.out.println("✓ Unique constraint 제거 완료 (BEFORE 시나리오)");
                } catch (Exception e) {
                        // 이미 제거된 경우 무시
                }
        }

        private void addUniqueConstraint() {
                try {
                        jdbcTemplate.execute(
                                        "ALTER TABLE url_clicks ADD CONSTRAINT uk_event_id UNIQUE (event_id)");
                        System.out.println("✓ Unique constraint 추가 완료 (AFTER 시나리오)");
                } catch (Exception e) {
                        // 이미 존재하는 경우 무시
                }
        }

        @Test
        @DisplayName("시나리오 1: BEFORE - enable.idempotence=false로 동일 이벤트 3번 발행하면 중복 저장됨")
        void testBefore_WithoutIdempotence_AllowsDuplicates() throws Exception {
                // Given: unique constraint 제거 (BEFORE 상태 재현)
                removeUniqueConstraint();

                // Given: idempotence OFF 설정
                Properties props = new Properties();
                props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
                props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
                props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
                props.put(ProducerConfig.ACKS_CONFIG, "1"); // acks=1 (리더만)
                props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "false"); // ❌ Idempotence OFF
                props.put(ProducerConfig.RETRIES_CONFIG, "0"); // 재시도 없음
                props.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);

                KafkaProducer<String, UrlClickedEvent> producer = new KafkaProducer<>(props);

                // When: 동일한 eventId를 가진 이벤트를 3번 발행
                long duplicateEventId = System.currentTimeMillis();
                String shortCode = "test-before";

                UrlClickedEvent event = new UrlClickedEvent(
                                duplicateEventId,
                                EventType.URL_CLICKED,
                                Instant.now(),
                                shortCode,
                                "https://example.com");

                // 3번 발행
                Future<RecordMetadata> future1 = producer
                                .send(new ProducerRecord<>(TopicType.URL_CLICKED.getTopicName(), shortCode, event));
                Future<RecordMetadata> future2 = producer
                                .send(new ProducerRecord<>(TopicType.URL_CLICKED.getTopicName(), shortCode, event));
                Future<RecordMetadata> future3 = producer
                                .send(new ProducerRecord<>(TopicType.URL_CLICKED.getTopicName(), shortCode, event));

                // 전송 완료 대기
                future1.get();
                future2.get();
                future3.get();
                producer.close();

                // Consumer 처리 대기
                await().atMost(10, SECONDS).untilAsserted(() -> {
                        Integer count = jdbcTemplate.queryForObject(
                                        "SELECT COUNT(*) FROM url_clicks WHERE short_code = ?",
                                        Integer.class,
                                        shortCode);
                        assertThat(count).isGreaterThan(1); // 중복 저장됨
                });

                // Then: 중복이 저장됨 (unique constraint가 없으므로)
                Integer totalCount = jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM url_clicks WHERE event_id = ?",
                                Integer.class,
                                duplicateEventId);

                System.out.println("=== BEFORE 테스트 결과 ===");
                System.out.println("발행 횟수: 3회");
                System.out.println("DB 저장 개수: " + totalCount);
                System.out.println("결과: " + (totalCount > 1 ? "중복 발생 ❌" : "중복 방지 ✓"));

                // 검증: 중복이 발생했음을 확인
                assertThat(totalCount).isGreaterThan(1)
                                .withFailMessage("BEFORE 설정에서는 중복이 발생해야 합니다");
        }

        @Test
        @DisplayName("시나리오 2: AFTER - enable.idempotence=true로 동일 이벤트 3번 발행하면 1개만 저장됨")
        void testAfter_WithIdempotence_PreventsDuplicates() throws Exception {
                // Given: unique constraint 추가 (AFTER 상태 재현)
                addUniqueConstraint();

                // Given: idempotence ON 설정
                Properties props = new Properties();
                props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
                props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
                props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
                props.put(ProducerConfig.ACKS_CONFIG, "all"); // acks=all (모든 replica)
                props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true"); // ✅ Idempotence ON
                props.put(ProducerConfig.RETRIES_CONFIG, "3"); // 재시도 3번
                props.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, "5");
                props.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);

                KafkaProducer<String, UrlClickedEvent> producer = new KafkaProducer<>(props);

                // When: 동일한 eventId를 가진 이벤트를 3번 발행
                long duplicateEventId = System.currentTimeMillis();
                String shortCode = "test-after";

                UrlClickedEvent event = new UrlClickedEvent(
                                duplicateEventId,
                                EventType.URL_CLICKED,
                                Instant.now(),
                                shortCode,
                                "https://example.com");

                // 3번 발행
                Future<RecordMetadata> future1 = producer
                                .send(new ProducerRecord<>(TopicType.URL_CLICKED.getTopicName(), shortCode, event));
                Future<RecordMetadata> future2 = producer
                                .send(new ProducerRecord<>(TopicType.URL_CLICKED.getTopicName(), shortCode, event));
                Future<RecordMetadata> future3 = producer
                                .send(new ProducerRecord<>(TopicType.URL_CLICKED.getTopicName(), shortCode, event));

                // 전송 완료 대기
                future1.get();
                future2.get();
                future3.get();
                producer.close();

                // Consumer 처리 대기
                await().atMost(10, SECONDS).untilAsserted(() -> {
                        Integer count = jdbcTemplate.queryForObject(
                                        "SELECT COUNT(*) FROM url_clicks WHERE short_code = ?",
                                        Integer.class,
                                        shortCode);
                        assertThat(count).isGreaterThanOrEqualTo(1); // 최소 1개는 저장됨
                });

                // Then: unique constraint로 인해 1개만 저장됨
                Integer totalCount = jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM url_clicks WHERE event_id = ?",
                                Integer.class,
                                duplicateEventId);

                System.out.println("\n=== AFTER 테스트 결과 ===");
                System.out.println("발행 횟수: 3회");
                System.out.println("DB 저장 개수: " + totalCount);
                System.out.println("결과: " + (totalCount == 1 ? "중복 방지 ✓" : "중복 발생 ❌"));

                // 검증: 중복이 방지되었음을 확인
                assertThat(totalCount).isEqualTo(1)
                                .withFailMessage("AFTER 설정에서는 정확히 1개만 저장되어야 합니다");
        }

        @Test
        @DisplayName("비교: BEFORE vs AFTER - 멱등성 설정 효과 검증")
        void testComparison_BeforeVsAfter() throws Exception {
                System.out.println("\n" + "=".repeat(60));
                System.out.println("Exactly-Once 멱등성 Before/After 비교");
                System.out.println("=".repeat(60));

                // BEFORE 시나리오
                testBefore_WithoutIdempotence_AllowsDuplicates();

                // AFTER 시나리오
                testAfter_WithIdempotence_PreventsDuplicates();

                System.out.println("\n" + "=".repeat(60));
                System.out.println("결론:");
                System.out.println("  BEFORE: enable.idempotence=false → 중복 발생 ❌");
                System.out.println("  AFTER:  enable.idempotence=true + unique constraint → Exactly-Once 보장 ✓");
                System.out.println("=".repeat(60) + "\n");
        }
}
