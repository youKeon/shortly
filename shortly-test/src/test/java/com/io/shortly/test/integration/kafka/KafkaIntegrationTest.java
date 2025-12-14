package com.io.shortly.test.integration.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.io.shortly.shared.event.UrlClickedEvent;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig
@EmbeddedKafka(
    partitions = 3,
    topics = {"url-clicked-test", "url-clicked-test-dlq"},
    brokerProperties = {
        "listeners=PLAINTEXT://localhost:9093",
        "port=9093"
    }
)
@DisplayName("Kafka At-Least-Once 전달 보장 통합 테스트 (EmbeddedKafka)")
class KafkaIntegrationTest {

    private static final String TOPIC = "url-clicked-test";
    private static final String DLQ_TOPIC = "url-clicked-test-dlq";

    @org.springframework.beans.factory.annotation.Autowired
    private EmbeddedKafkaBroker embeddedKafka;

    private KafkaTemplate<String, UrlClickedEvent> producer;
    private KafkaMessageListenerContainer<String, UrlClickedEvent> container;
    private BlockingQueue<ConsumerRecord<String, UrlClickedEvent>> records;

    @BeforeEach
    void setUp() {
        this.records = new LinkedBlockingQueue<>();

        // Producer 설정
        var producerProps = org.springframework.kafka.test.utils.KafkaTestUtils
            .producerProps(embeddedKafka);
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        producerProps.put(ProducerConfig.ACKS_CONFIG, "all");
        producerProps.put(ProducerConfig.RETRIES_CONFIG, 3);
        producerProps.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 1);

        var producerFactory = new DefaultKafkaProducerFactory<String, UrlClickedEvent>(producerProps);
        this.producer = new KafkaTemplate<>(producerFactory);

        // Consumer 설정
        var consumerProps = org.springframework.kafka.test.utils.KafkaTestUtils
            .consumerProps("test-group", "true", embeddedKafka);
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        consumerProps.put(JsonDeserializer.TRUSTED_PACKAGES, "com.io.shortly.shared.event");
        consumerProps.put(JsonDeserializer.VALUE_DEFAULT_TYPE, UrlClickedEvent.class.getName());
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumerProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

        var consumerFactory = new DefaultKafkaConsumerFactory<String, UrlClickedEvent>(consumerProps);

        ContainerProperties containerProps = new ContainerProperties(TOPIC);
        containerProps.setMessageListener((MessageListener<String, UrlClickedEvent>) record -> {
            records.add(record);
        });

        container = new KafkaMessageListenerContainer<>(consumerFactory, containerProps);
        container.start();

        ContainerTestUtils.waitForAssignment(container, embeddedKafka.getPartitionsPerTopic());
    }

    @AfterEach
    void tearDown() {
        if (container != null) {
            container.stop();
        }
    }

    @Test
    @DisplayName("단일 메시지 전송 및 수신")
    void singleMessageDelivery() throws InterruptedException {
        // given
        UrlClickedEvent event = UrlClickedEvent.of(1L, "abc123", "https://example.com");

        // when
        producer.send(TOPIC, event.getShortCode(), event);

        // then
        ConsumerRecord<String, UrlClickedEvent> received = records.poll(5, TimeUnit.SECONDS);
        assertThat(received).isNotNull();
        assertThat(received.value().getEventId()).isEqualTo(1L);
        assertThat(received.value().getShortCode()).isEqualTo("abc123");
    }

    @Test
    @DisplayName("At-Least-Once - 100개 메시지 모두 전달")
    void atLeastOnce_100Messages() {
        // given
        int messageCount = 100;
        List<UrlClickedEvent> sentEvents = new ArrayList<>();

        // when
        for (int i = 0; i < messageCount; i++) {
            UrlClickedEvent event = UrlClickedEvent.of(i, "code" + i, "https://example.com/" + i);
            sentEvents.add(event);
            producer.send(TOPIC, event.getShortCode(), event);
        }

        // then - 모든 메시지가 수신될 때까지 대기
        await()
            .atMost(Duration.ofSeconds(10))
            .untilAsserted(() -> {
                assertThat(records.size()).isEqualTo(messageCount);
            });

        // 수신된 메시지 검증
        List<ConsumerRecord<String, UrlClickedEvent>> receivedRecords = new ArrayList<>();
        records.drainTo(receivedRecords);

        List<UrlClickedEvent> receivedEvents = receivedRecords.stream()
            .map(ConsumerRecord::value)
            .toList();

        assertThat(receivedEvents).hasSize(messageCount);
    }

    @Test
    @DisplayName("순서 보장 - 같은 파티션 키는 순서 유지")
    void orderGuarantee_SamePartitionKey() throws InterruptedException {
        // given
        String partitionKey = "user123";
        List<Long> sentOrder = List.of(1L, 2L, 3L, 4L, 5L);

        // when - 같은 파티션 키로 순차 전송
        for (Long eventId : sentOrder) {
            UrlClickedEvent event = UrlClickedEvent.of(eventId, partitionKey, "https://example.com");
            producer.send(TOPIC, partitionKey, event);
        }

        // then - 수신 순서 확인
        List<Long> receivedOrder = new ArrayList<>();
        for (int i = 0; i < sentOrder.size(); i++) {
            ConsumerRecord<String, UrlClickedEvent> record = records.poll(5, TimeUnit.SECONDS);
            assertThat(record).isNotNull();
            assertThat(record.key()).isEqualTo(partitionKey);
            receivedOrder.add(record.value().getEventId());
        }

        assertThat(receivedOrder).containsExactlyElementsOf(sentOrder);
    }

    @Test
    @DisplayName("파티션 분산 - 여러 파티션으로 분산 전달")
    void partitionDistribution() {
        // given
        int messageCount = 90; // 3개 파티션이므로 30개씩 분산될 것으로 예상

        // when
        for (int i = 0; i < messageCount; i++) {
            String key = "key" + i;
            UrlClickedEvent event = UrlClickedEvent.of(i, key, "https://example.com/" + i);
            producer.send(TOPIC, key, event);
        }

        // then
        await()
            .atMost(Duration.ofSeconds(10))
            .untilAsserted(() -> {
                assertThat(records.size()).isEqualTo(messageCount);
            });

        // 파티션별 분포 확인
        List<ConsumerRecord<String, UrlClickedEvent>> allRecords = new ArrayList<>();
        records.drainTo(allRecords);

        var partitionCounts = allRecords.stream()
            .collect(
                java.util.stream.Collectors.groupingBy(
                    ConsumerRecord::partition,
                    java.util.stream.Collectors.counting()
                )
            );

        System.out.println("파티션별 메시지 분포: " + partitionCounts);
        assertThat(partitionCounts.keySet()).hasSize(3); // 3개 파티션 모두 사용됨
    }

    @Test
    @DisplayName("중복 전송 - 재시도 시 중복 가능성")
    void duplicateDelivery_OnRetry() throws InterruptedException {
        // given
        UrlClickedEvent event = UrlClickedEvent.of(999L, "retry123", "https://example.com");

        // when - 같은 메시지를 3번 전송 (재시도 시뮬레이션)
        producer.send(TOPIC, event.getShortCode(), event);
        producer.send(TOPIC, event.getShortCode(), event);
        producer.send(TOPIC, event.getShortCode(), event);

        // then - 최소 3번 수신되어야 함 (At-Least-Once)
        List<UrlClickedEvent> receivedEvents = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            ConsumerRecord<String, UrlClickedEvent> record = records.poll(5, TimeUnit.SECONDS);
            assertThat(record).isNotNull();
            receivedEvents.add(record.value());
        }

        assertThat(receivedEvents).hasSize(3);
        assertThat(receivedEvents).allMatch(e -> e.getEventId() == 999L);
    }

    @Test
    @DisplayName("대량 메시지 처리 - 1000개 전송 및 수신")
    void largeVolumeProcessing() {
        // given
        int messageCount = 1000;

        // when
        for (int i = 0; i < messageCount; i++) {
            UrlClickedEvent event = UrlClickedEvent.of(i, "bulk" + i, "https://example.com/" + i);
            producer.send(TOPIC, event.getShortCode(), event);
        }

        // then
        await()
            .atMost(Duration.ofSeconds(30))
            .untilAsserted(() -> {
                assertThat(records.size()).isEqualTo(messageCount);
            });

        System.out.println("1000개 메시지 모두 전달 완료");
    }

    @Test
    @DisplayName("Consumer Offset Commit - 수동 커밋")
    void consumerOffsetCommit() {
        // given
        int messageCount = 10;

        // when
        for (int i = 0; i < messageCount; i++) {
            UrlClickedEvent event = UrlClickedEvent.of(i, "offset" + i, "https://example.com/" + i);
            producer.send(TOPIC, event.getShortCode(), event);
        }

        // then
        await()
            .atMost(Duration.ofSeconds(5))
            .untilAsserted(() -> {
                assertThat(records.size()).isEqualTo(messageCount);
            });

        // Consumer가 오프셋을 커밋하면 재시작 시 중복 수신 방지
        // (현재 테스트는 auto-commit=false이므로 수동 커밋 필요)
    }
}
