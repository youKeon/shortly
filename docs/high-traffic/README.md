# 대규모 트래픽 처리 시 발생할 수 있는 문제

## 1. Short Code 충돌 발생 빈도 증가

### 문제 상황
1. 6자리 Base62 랜덤 short code 생성 → 62^6(약 568억) 공간.
2. 초당 10,000건 이상 생성 시 Birthday Paradox 영향으로 충돌 확률 증가.
3. 충돌 시 최대 5회 재시도 → 응답 지연, DB 쓰기/읽기 반복.

### 해결 방안

#### 1. Snowflake 기반 Short Code 생성
1. 각 인스턴스에 고유한 worker/datacenter ID를 할당.
2. 64비트 Snowflake ID를 생성 후 Base62로 변환 → 짧은 short code 확보.
3. 충돌 없음 → 재시도 로직 제거, 일정한 응답 시간.

#### 2. Redis INCR + Base62
1. Redis `INCR`로 전역 순차 ID 생성.
2. Base62 인코딩하여 short code 발급.
3. Fallback(예: Snowflake)과 `min-replicas-to-write`, `WAIT` 등 내구성 설정 필수.

### 선택: Snowflake

- **이유:**
  - 중앙 카운터 없이 애플리케이션 내에서 독립적으로 ID 생성.
  - 인스턴스 기동 시 worker/datacenter ID만 설정하면 운영 간결.
  - Base62 변환으로 6자리 short code 유지 가능.
  - Redis 장애/복구에 따른 중복 위험, 주기적 초기화 등 운영 부담을 제거.
  - Redis INCR은 쓰기 내구성을 높이려면 `WAIT`, AOF 동기화 등을 켜야 해 응답 지연이 늘고, 설정을 완화하면 replication lag로 중복 위험이 커짐.
  - 캐시·통계를 같은 Redis에 두면 카운터 장애가 전 서비스로 확산될 수 있어 인프라 격리·모니터링 비용이 증가.

---

## 2. Database Connection Pool 고갈

### 문제 상황
1. HikariCP 최대 연결 수: 10개 (URL Service), 20개 (Redirect/Click Service).
2. 10,000+ TPS에서 동시 요청 수가 Connection Pool 크기를 초과.
3. Connection 대기 시간 증가 → 응답 시간 증가 → Timeout 발생.
4. URL Service는 짧은 트랜잭션이지만, Click Service는 Kafka 이벤트 소비로 장기 Connection 점유 가능.

### 해결 방안

#### 1. Connection Pool 크기 확대
```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 50  # URL Service
      maximum-pool-size: 100 # Click Service
      connection-timeout: 5000
      idle-timeout: 300000
```
- Trade-off: DB 서버의 최대 연결 수 제한 고려 필요.

#### 2. Connection 사용 최적화
- `@Transactional(readOnly = true)` 적극 활용으로 읽기 전용 Connection 분리.
- 트랜잭션 범위 최소화 (불필요한 로직을 트랜잭션 외부로 이동).

#### 3. Read Replica 도입
- Master-Slave 구조로 읽기 부하 분산.
- Click Service의 분석 API (`/stats`, `/details`)는 Replica에서 조회.

---

## 3. Redis Cache 장애 시 DB 과부하

### 문제 상황
1. Redirect Service는 Cache-First 전략 (Caffeine L1 + Redis L2).
2. Redis 장애 또는 네트워크 이슈 발생 시, 모든 요청이 DB로 직접 전달.
3. 10,000+ TPS가 DB로 몰리면 DB Connection Pool 고갈 및 응답 시간 폭증.

### 해결 방안

#### 1. Circuit Breaker 패턴
- Resilience4j Circuit Breaker를 Redis 호출에 적용.
- Redis 장애 감지 시 Circuit Open → DB 직접 조회 대신 Caffeine L1 캐시만 사용.
- L1 캐시 미스 시 404 반환 가능.

#### 2. Request Coalescing
- 동일한 short code에 대한 동시 요청을 병합하여 DB 조회 1회로 처리.
- Caffeine Cache의 `refreshAfterWrite` 기능 활용.

#### 3. Rate Limiting
- Redis 장애 시 DB 조회를 허용하되, Rate Limiter로 초당 요청 수 제한.
- ex) Bucket4j로 서버당 1000 TPS로 제한하여 DB 보호.

---

## 5. Kafka Consumer Lag 증가

### 문제 상황
1. Click Service는 `url-clicked` 이벤트를 Kafka에서 소비하여 DB에 저장.
2. 10,000+ TPS에서 초당 약 10,000개 이상의 클릭 이벤트 발생.
3. Consumer의 처리 속도가 Producer보다 느리면 Lag 증가.
4. Lag이 누적되면 클릭 데이터 저장 지연 → 실시간 분석 API 응답 지연.
5. DB INSERT 성능 병목 (단건 INSERT로는 처리 한계).

### 해결 방안

#### 1. Batch Insert 도입
```java
@Transactional
public void batchInsert(List<ClickEvent> events) {
    jdbcTemplate.batchUpdate(
        "INSERT INTO click (short_code, clicked_at, ip_address, user_agent) VALUES (?, ?, ?, ?)",
        events,
        100,  // Batch size
        (ps, event) -> {
            ps.setString(1, event.getShortCode());
            ps.setTimestamp(2, Timestamp.from(event.getClickedAt()));
            ps.setString(3, event.getIpAddress());
            ps.setString(4, event.getUserAgent());
        }
    );
}
```
- 단건 INSERT 대비 10배 이상 성능 향상.

#### 2. Consumer Parallelism 증가
```yaml
spring:
  kafka:
    consumer:
      concurrency: 10  # Consumer 스레드 수 증가
```
- Kafka 파티션 수와 동일하게 설정하여 병렬 처리.

#### 3. 비동기 처리 + Buffer
- `@KafkaListener`에서 수신한 이벤트를 메모리 큐에 버퍼링.
- 별도 스레드에서 주기적으로 Batch Insert 실행.

---

## 6. Click 데이터 테이블 무한 증가

### 문제 상황
1. Click Service의 `click` 테이블은 모든 클릭 이벤트를 저장 (무한 증가).
2. 10,000+ TPS로 하루 약 8.6억 건 이상의 레코드 생성 (10,000 × 86,400).
3. 테이블 크기 증가 → 인덱스 크기 증가 → 조회 성능 저하.
4. `/api/v1/clicks/{shortCode}/details` API 응답 시간 증가.

### 해결 방안

#### 1. 파티셔닝 (Range Partitioning by `clicked_at`)
```sql
CREATE TABLE click (
    id BIGINT AUTO_INCREMENT,
    short_code VARCHAR(6) NOT NULL,
    clicked_at TIMESTAMP(6) NOT NULL,
    ip_address VARCHAR(45),
    user_agent TEXT,
    PRIMARY KEY (id, clicked_at),
    INDEX idx_short_code (short_code)
) PARTITION BY RANGE (YEAR(clicked_at) * 100 + MONTH(clicked_at)) (
    PARTITION p202410 VALUES LESS THAN (202411),
    PARTITION p202411 VALUES LESS THAN (202412),
    PARTITION p202412 VALUES LESS THAN (202501),
    PARTITION pmax VALUES LESS THAN MAXVALUE
);
```
- 월별 파티션으로 쿼리 성능 유지.
- 오래된 파티션은 주기적으로 삭제 또는 아카이빙.

#### 2. Hot/Cold Data 분리
- 최근 30일 데이터만 MySQL에 유지 (Hot Data).
- 30일 이전 데이터는 S3, Parquet 등으로 이동 (Cold Data).
- 분석 쿼리는 데이터 레이크에서 처리.

#### 3. Time-Series Database 도입
- InfluxDB, TimescaleDB 등 시계열 DB로 클릭 데이터 저장.
- 자동 데이터 롤업 및 보존 정책 설정 가능.

---

## 7. Outbox Event 테이블 증가

### 문제 상황
1. URL Service의 `outbox` 테이블은 모든 `UrlCreatedEvent`를 저장.
2. 10,000+ TPS로 하루 약 8.6억 건 이상의 Outbox 이벤트 생성.
3. 발행 완료된 이벤트(`PUBLISHED` 상태)가 삭제되지 않으면 테이블 무한 증가.
4. Outbox Scheduler의 폴링 성능 저하 (`PENDING` 상태 조회 시 Full Scan 위험).

### 해결 방안

#### 1. 발행 완료 이벤트 자동 삭제
```java
@Scheduled(cron = "0 0 2 * * *")  // 매일 새벽 2시
@Transactional
public void cleanupPublishedEvents() {
    Instant cutoff = Instant.now().minus(7, ChronoUnit.DAYS);
    outboxJpaRepository.deleteByStatusAndCreatedAtBefore(
        OutboxEventStatus.PUBLISHED,
        cutoff
    );
    log.info("Deleted published outbox events older than {}", cutoff);
}
```
- 7일 이전의 `PUBLISHED` 이벤트 삭제.

#### 2. 파티셔닝 적용
- `created_at` 기준 Range Partitioning으로 오래된 파티션 삭제.

#### 3. Compound Index 최적화
```sql
CREATE INDEX idx_status_created_at ON outbox (status, created_at);
```
- `status = 'PENDING'` 조회 시 인덱스 활용으로 성능 보장.

---

## 8. Redirect Service의 R2DBC Connection Pool 고갈

### 문제 상황
1. Redirect Service는 WebFlux + R2DBC로 비동기 처리 (높은 동시성).
2. R2DBC Connection Pool 크기: `initialSize=10, maxSize=20`.
3. 10,000+ TPS에서 동시 요청 수가 20개를 초과하면 Connection 대기 발생.
4. 비동기 환경에서도 DB Connection은 물리적 제약으로 한계 존재.

### 해결 방안

#### 1. Connection Pool 크기 확대
```yaml
spring:
  r2dbc:
    pool:
      initial-size: 50
      max-size: 200
      max-idle-time: 30m
```
- Trade-off: DB 서버 최대 연결 수 제한 고려.

#### 2. Cache-First 전략 강화
- Redirect Service는 이미 Cache-First 전략 사용.
- Cache Hit Rate를 95% 이상으로 유지하여 DB 조회 최소화.
- Caffeine 캐시 크기 확대 및 TTL 조정.

#### 3. Database Query 제거 검토
- Redirect Service의 비즈니스 요구사항 상 DB 조회가 필요한지 재검토.
- Cache Miss 시 404 반환 정책으로 전환 가능한지 협의.

---

## 9. Base62 알고리즘의 SHA-256 해시 병목

### 문제 상황
1. 현재 Base62 알고리즘은 `SHA-256(originalUrl + nanoTime)` 계산 후 Base62 인코딩.
2. SHA-256 해시 연산은 CPU 집약적 작업.
3. 10,000+ TPS에서 초당 약 10,000번 이상의 SHA-256 계산 발생.
4. 충돌 시 재시도로 최대 5배의 해시 연산 추가 발생 가능.

### 해결 방안

#### 1. 경량 해시 함수로 교체
- SHA-256 → MurmurHash3, xxHash 등 경량 해시 함수.
- 충돌 저항성은 낮지만, 6자리 short code 생성에는 충분.
- CPU 사용률 감소로 처리량 증가.

#### 2. 해시 연산 캐싱
- 동일한 `originalUrl`에 대한 해시 결과를 캐싱.
- Trade-off: 동일 URL에 대해 항상 같은 short code 생성 (멱등성).

#### 3. Pre-generated Short Code Pool
- 미리 short code를 생성하여 Redis에 저장.
- URL 단축 요청 시 Pool에서 꺼내서 사용.
- Trade-off: Pool 관리 복잡도 증가, 메모리 사용량 증가.

---

## 10. Caffeine L1 캐시 메모리 부족 (OOM)

### 문제 상황
1. Redirect Service의 Caffeine L1 캐시는 최대 10,000개 엔트리 저장.
2. 인기 있는 short code가 집중되면 캐시 히트율은 높지만, 메모리 사용량 증가.
3. 각 엔트리의 크기가 크면 (긴 original URL, 메타데이터) 10,000개도 메모리 부족 가능.
4. OutOfMemoryError 발생 시 서비스 다운타임.

### 해결 방안

#### 1. 캐시 크기 동적 조정
```java
Caffeine.newBuilder()
    .maximumWeight(100_000_000)  // 100MB
    .weigher((String key, RedirectCacheValue value) ->
        key.length() + value.getOriginalUrl().length())
    .build();
```
- 엔트리 개수가 아닌 메모리 크기 기준으로 제한.

#### 2. TTL 단축
```java
Caffeine.newBuilder()
    .expireAfterWrite(5, TimeUnit.MINUTES)  // 5분 후 만료
    .build();
```
- 자주 사용되지 않는 엔트리를 빠르게 제거하여 메모리 확보.

#### 3. 메모리 모니터링 및 알람
- Prometheus + Grafana로 JVM 힙 메모리 사용률 모니터링.
- 80% 이상 시 알람 발생 및 캐시 크기 자동 조정.

---

## 11. Kafka 파티션 불균형으로 인한 Consumer 병목

### 문제 상황
1. `url-created`, `url-clicked` 토픽의 파티션 수: 기본 1개 (설정되지 않은 경우).
2. 10,000+ TPS의 이벤트가 단일 파티션으로 전달되면 Consumer 처리 한계.
3. Consumer를 여러 개 띄워도 파티션이 1개이면 1개의 Consumer만 동작.
4. Consumer Lag 증가 → 실시간 처리 지연.

### 해결 방안

#### 1. 파티션 수 증가
```bash
kafka-topics --bootstrap-server localhost:9092 \
  --alter --topic url-created --partitions 10

kafka-topics --bootstrap-server localhost:9092 \
  --alter --topic url-clicked --partitions 10
```
- 파티션 10개 → Consumer 10개 병렬 처리 가능.

#### 2. Partitioning Key 전략
- `shortCode`를 파티셔닝 키로 사용하여 동일한 short code 이벤트는 같은 파티션으로 전달.
- 순서 보장 필요 시 유리.

#### 3. Consumer Group 설정
```yaml
spring:
  kafka:
    consumer:
      group-id: click-service-group
      concurrency: 10  # 파티션 수와 동일
```
- Consumer 스레드 수를 파티션 수와 맞춰 최대 처리량 확보.

---
