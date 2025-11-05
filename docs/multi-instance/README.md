# 멀티 인스턴스 환경에서 발생 가능한 문제

## 1. L1 캐시 불일치 문제 ✅ 해결됨

### 문제 상황
- shortly-redirect-service의 application.yml은 아래 코드를 통해 모든 인스턴스가 하나의 consumer group으로 묶임.
  - `group-id: ${KAFKA_CONSUMER_GROUP:redirect-service-group}`
- 이벤트를 하나의 인스턴스가 소비하면 나머지 인스턴스는 소비할 수 없음.
- 나머지 인스턴스는 L1(Caffeine)에 저장되지 않아 사용자마다 응답 시간이 달라짐.

### 해결 방안
#### Kafka Broadcast 패턴
- 인스턴스마다 고유한 group-id를 부여하여 모든 인스턴스가 이벤트를 수신.
- 간단한 구현과 캐시 일관성 보장.
- Kafka 부하 증가(이벤트 처리 횟수가 N배 증가).
- 모든 인스턴스가 DB INSERT 시도.

#### Redis Pub/Sub
- 이벤트가 발행되면 Redis Pub/Sub으로 모든 인스턴스에게 데이터 적재 메시지 브로드캐스트.
- 낮은 네트워크 통신 비용
- 메시지 손실 위험 및 복잡도 증가.

#### L1 캐시 제거
- Caffeine 기반의 로컬 캐시를 제거하고 Redis 글로벌 캐시만 사용.
- 캐시 일관성 보장 및 단순한 구현.
- 성능 저하(모든 요청이 네트워크 통신).
- Redis 부하 증가.

### 선택: Redis Pub/Sub
- Redis Pub/Sub 지연이 짧아 L1 캐시 정합성을 빠르게 맞출 수 있으며, Kafka Broadcast 패턴 대비 Kafka/DB 부하가 인스턴스 수만큼 증가하는 문제를 피함.
- URL 생성 요청은 전체의 5% 미만인 read-heavy 구조이므로, 생성 이벤트를 모든 인스턴스가 중복 처리하는 Broadcast보다 빠르게 워밍하는 것이 효율적이라고 판단.
- Kafka Consumer 구조를 그대로 유지하면서도 이벤트 후속 처리를 각 인스턴스에 전파할 수 있어, 기존 설계 변경 범위를 최소화.

## 2. Click 이벤트 중복 저장 문제

### 문제 상황
- `shortly-click-service`의 Kafka 설정: `ack-mode: batch` (배치 커밋)
- `UrlClickedEventConsumer`는 이벤트를 받아 DB INSERT 수행.
- 멱등성 보장이 없음: 동일한 이벤트를 여러 번 처리할 수 있음.

**재현 시나리오:**
```
[t0] Instance 1: UrlClickedEvent(eventId=abc-123) 수신
[t1] Instance 1: DB 저장 완료 (id=1, shortCode=xyz)
[t2] Instance 1: 배치 커밋 전 장애 발생 → ack 실패
[t3] Kafka: 동일 메시지 재전송 (eventId=abc-123)
[t4] Instance 2: 같은 이벤트 재소비 → DB 저장 (id=2, ...)

→ 동일 클릭이 2번 저장됨
```

**근본 원인:**
- `UrlClickJpaEntity`에 `eventId` 필드가 없음
- DB에 unique constraint가 없어서 중복 INSERT 허용
- `BaseEvent.eventId`(UUID)를 활용하지 않음

**영향:**
- 클릭 통계 부정확 (중복 카운팅)
- 데이터 정합성 문제
- 배치 처리 중 장애 시 전체 배치 재처리 → 대량 중복 가능

### 해결 방안

#### Option 1: eventId 기반 멱등성 (권장)
```java
// UrlClickJpaEntity에 필드 추가
@Column(name = "event_id", unique = true, nullable = false, length = 36)
private String eventId;

// Consumer 로직 수정
public void consumeUrlClicked(UrlClickedEvent event) {
    if (urlClickRepository.existsByEventId(event.getEventId())) {
        log.debug("이미 처리된 이벤트: eventId={}", event.getEventId());
        return; // 중복 처리 방지
    }
    UrlClick urlClick = UrlClick.create(event.getEventId(), ...);
    urlClickRepository.save(urlClick);
}
```

**장점:**
- 완벽한 멱등성 보장
- DB unique constraint로 안전장치 추가
- 배치 커밋 실패 시에도 중복 방지

**단점:**
- 도메인 모델에 인프라 관심사(eventId) 침투
- 매 저장 전 `existsByEventId()` 조회 오버헤드

#### Option 2: ack-mode를 manual로 변경
```yaml
spring:
  kafka:
    listener:
      ack-mode: manual  # batch → manual
```

**장점:**
- 메시지 단위로 즉시 커밋 → 재처리 범위 최소화
- 구현 단순 (코드 변경 불필요)

**단점:**
- 멱등성 문제 근본 해결 아님
- 처리 중 장애 시 여전히 중복 가능
- 배치 커밋 대비 성능 저하 (커밋 횟수 증가)

#### Option 3: 복합 Unique Index (시간 기반)
```sql
CREATE UNIQUE INDEX uk_click_dedup
ON url_clicks (short_code, clicked_at, original_url);
```

**장점:**
- eventId 없이 비즈니스 필드로 중복 방지
- 도메인 모델 순수성 유지

**단점:**
- 동일 shortCode를 같은 초에 여러 번 클릭 시 오작동
- LocalDateTime 정밀도 한계 (밀리초 단위)
- 완벽한 멱등성 보장 불가

### 선택: Option 1 (eventId 기반 멱등성) - 미구현

**선택 근거:**
- Kafka 이벤트 재처리는 불가피한 시나리오 (네트워크 장애, 리밸런싱)
- `BaseEvent`가 이미 고유 `eventId`를 제공하므로 이를 활용하는 것이 자연스러움
- DB unique constraint로 이중 안전장치 구축
- 클릭 통계 정확성이 비즈니스 핵심 지표이므로 완벽한 멱등성 필요

**구현 필요 사항:**
1. `UrlClick` 도메인에 `eventId` 추가
2. `UrlClickJpaEntity`에 `eventId` 컬럼 추가 (unique constraint)
3. `UrlClickedEventConsumer`에 중복 체크 로직 추가
4. `UrlClickRepository`에 `existsByEventId()` 메서드 추가

---

## 3. Short Code 충돌 증가 (P1 - High)

### 문제 상황
- 현재 생성 로직: `SHA-256(originalUrl + System.nanoTime()) → Base62 인코딩`
- 충돌 발생 시 최대 5회 재시도 (DB unique constraint 의존)
- 멀티 인스턴스 환경에서 동시 요청 시 충돌 확률 증가

**시나리오:**
```
[동일 URL에 대한 동시 요청]
Instance 1: nanoTime=123456789000 → shortCode="aB12cD"
Instance 2: nanoTime=123456789001 → shortCode="aB12cD" (충돌!)
Instance 3: nanoTime=123456789002 → shortCode="eF34gH"

→ Instance 2는 재시도 (최대 5회)
→ 고부하 시 재시도 소진 → 503 Error
```

**근본 원인:**
- `System.nanoTime()`은 단조 증가하지만 인스턴스 간 독립적
- 동일 입력(originalUrl)에 대해 각 인스턴스가 다른 salt 생성
- 해시 충돌 + Base62 인코딩 후 6자리 제한 → 충돌 확률 증가

**영향:**
- TPS 증가 시 재시도 빈번 발생 → 응답 지연
- 최악의 경우 생성 실패 → 사용자 경험 저하
- DB 부하 증가 (재시도마다 INSERT 시도)

### 해결 방안

#### Option 1: 분산 ID 생성기 (Snowflake)
```java
// Twitter Snowflake 알고리즘
// [41bit timestamp][10bit machine ID][12bit sequence] = 63bit
public String generate(String seed) {
    long id = snowflake.nextId();  // 전역 고유 ID
    return Base62.encode(id);
}
```

**장점:**
- 충돌 확률 0% (전역 고유성 보장)
- 재시도 불필요 → 성능 향상
- 시간 정렬 가능 (timestamp 포함)

**단점:**
- 인스턴스별 고유 ID 할당 관리 필요 (Zookeeper, Consul)
- 구현 복잡도 증가
- shortCode 길이 증가 가능 (6자리 → 7-8자리)

#### Option 2: Redis INCR 기반 시퀀스
```java
public String generate(String seed) {
    long sequence = redisTemplate.opsForValue().increment("url:sequence");
    return Base62.encode(sequence);
}
```

**장점:**
- 단순한 구현
- 충돌 없음 (Redis atomic operation)
- 짧은 shortCode 유지 가능

**단점:**
- Redis 의존성 증가 (SPOF 위험)
- Redis 장애 시 서비스 불가
- 시퀀스 예측 가능 (보안 이슈)

#### Option 3: 인스턴스별 ID 범위 할당
```java
// Instance 1: 0-999999
// Instance 2: 1000000-1999999
// Instance 3: 2000000-2999999
public String generate(String seed) {
    long sequence = instanceBaseId + localCounter.incrementAndGet();
    return Base62.encode(sequence);
}
```

**장점:**
- 외부 의존성 없음
- 충돌 없음
- 간단한 구현

**단점:**
- 인스턴스 수 변경 시 재설정 필요
- Auto-scaling 환경에 부적합
- ID 범위 고갈 관리 필요

### 선택: 현재 알고리즘 유지 + 모니터링 강화 - 추후 검토

**선택 근거:**
- 현재 17,781 TPS에서 충돌 비율 측정 필요
- 5회 재시도로도 충돌 해결 가능하다면 추가 복잡도 불필요
- Snowflake 도입 시 인프라 복잡도 증가 (Zookeeper 등)
- Redis INCR은 SPOF 위험

**모니터링 지표:**
- `short_code_generation_retry_count`: 재시도 횟수 (P95, P99)
- `short_code_generation_failure_rate`: 생성 실패율 (목표: <0.01%)
- 재시도 횟수가 평균 2회 초과 시 Option 1 고려

---

## 4. Database Connection Pool 고갈 (P1 - High)

### 문제 상황
- 각 서비스의 HikariCP 설정이 멀티 인스턴스를 고려하지 않음
- MySQL `max_connections` 기본값: 151
- 인스턴스 증가 시 총 연결 수가 급증

**계산 예시:**
```
[Before] 1 instance × 10 connections = 10 total
[After]  5 instances × 10 connections = 50 total

서비스 3개 × 5 인스턴스 × 10 connections = 150 connections
→ MySQL max_connections(151) 거의 소진
→ 모니터링, admin 연결 불가
```

**영향:**
- 신규 연결 요청 실패 → 503 Error
- Connection timeout 증가
- 스케일 아웃 제약 (인스턴스 추가 불가)

### 해결 방안

#### Option 1: 인스턴스당 Pool 크기 축소
```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 5  # 10 → 5
      minimum-idle: 2
```

**장점:**
- 간단한 설정 변경
- 즉시 적용 가능

**단점:**
- 단일 인스턴스 성능 저하
- 동시 요청 처리 능력 감소

#### Option 2: MySQL max_connections 증가
```yaml
# docker-compose-prod.yml
mysql-url:
  environment:
    MYSQL_MAX_CONNECTIONS: 500
```

**장점:**
- 애플리케이션 변경 불필요
- 여유 있는 연결 풀 유지

**단점:**
- MySQL 메모리 사용량 증가
- 하드웨어 스펙에 의존

#### Option 3: Connection Pool 동적 조정
```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: ${DB_POOL_SIZE:10}
      minimum-idle: ${DB_MIN_IDLE:2}
```

**장점:**
- 환경별 유연한 설정
- 개발/운영 분리 가능

**단점:**
- 배포 시마다 설정 관리 필요

### 선택: Option 1 + Option 2 조합 - 미적용

**선택 근거:**
- 인스턴스당 연결 수를 5로 축소해도 대부분의 워크로드 처리 가능
- MySQL max_connections를 500으로 증가하여 충분한 여유 확보
- 서비스 3개 × 10 인스턴스 × 5 connections = 150 (여유: 350)

**권장 설정:**
```yaml
# application.yml (모든 서비스)
spring:
  datasource:
    hikari:
      maximum-pool-size: 5
      minimum-idle: 2
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000

# docker-compose-prod.yml
mysql-url:
  environment:
    MYSQL_MAX_CONNECTIONS: 500
```

**모니터링:**
- HikariCP 메트릭: `hikaricp.connections.active`, `hikaricp.connections.pending`
- MySQL 메트릭: `Threads_connected`, `Max_used_connections`

---

## 5. Docker Compose 스케일링 제약 (P2 - Medium)

### 문제 상황
- 현재 `docker-compose-prod.yml`은 고정 컨테이너 이름과 포트 사용
- `docker-compose up --scale` 명령어 사용 불가

**예시:**
```yaml
url-service:
  container_name: shortly-url-service  # ← 고정 이름
  ports:
    - "8081:8081"  # ← 고정 포트
```

**스케일 업 시도 시:**
```bash
docker-compose up -d --scale url-service=3

# 실패 이유:
# 1. container_name 중복 → 두 번째 컨테이너 생성 불가
# 2. 호스트 포트 8081 충돌 → 세 컨테이너가 같은 포트 바인딩 시도
```

**영향:**
- 수동 스케일 아웃 불가
- 부하 증가 시 즉각 대응 불가
- Blue-Green 배포 제약

### 해결 방안

#### Option 1: Docker Compose 수정 (임시 해결)
```yaml
url-service:
  # container_name 제거 (자동 생성: shortly_url-service_1, _2, _3)
  ports:
    - "8081-8085:8081"  # 포트 범위 매핑
  deploy:
    replicas: 3  # Docker Swarm 모드 필요
```

**장점:**
- 최소한의 설정 변경
- 기존 인프라 유지

**단점:**
- Docker Swarm 모드 전환 필요 (단일 Docker Compose는 replicas 미지원)
- 로드 밸런싱 별도 구성 필요 (Nginx, HAProxy)
- 포트 범위 수동 관리

#### Option 2: Kubernetes 전환 (권장)
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: url-service
spec:
  replicas: 3
  selector:
    matchLabels:
      app: url-service
  template:
    spec:
      containers:
      - name: url-service
        image: shortly-url-service:latest
        ports:
        - containerPort: 8081
---
apiVersion: v1
kind: Service
metadata:
  name: url-service
spec:
  selector:
    app: url-service
  ports:
  - port: 80
    targetPort: 8081
```

**장점:**
- 자동 스케일링 (HPA: Horizontal Pod Autoscaler)
- 로드 밸런싱 내장 (Service)
- 무중단 배포 (Rolling Update)
- 헬스체크 및 자동 복구
- Production-ready 오케스트레이션

**단점:**
- 학습 곡선 (K8s 복잡도)
- 인프라 비용 증가 (Control Plane)
- 로컬 개발 환경 복잡도 증가

#### Option 3: 서비스별 포트 수동 할당
```yaml
url-service-1:
  container_name: url-service-1
  ports:
    - "8081:8081"

url-service-2:
  container_name: url-service-2
  ports:
    - "8091:8081"

url-service-3:
  container_name: url-service-3
  ports:
    - "8101:8081"
```

**장점:**
- Docker Compose 그대로 사용
- 즉시 적용 가능

**단점:**
- 설정 파일 비대화
- 수동 관리 필요
- Auto-scaling 불가

### 선택: 현재 구조 유지 + 추후 K8s 전환 검토

**선택 근거:**
- 현재 단계에서는 고정된 인스턴스 수로 충분 (성능 목표 달성)
- K8s 전환은 운영 복잡도 대비 이득이 크지 않음 (초기 단계)
- 트래픽 증가 시 K8s 전환 계획 수립

**K8s 전환 시점 판단 기준:**
- 일일 트래픽 1억 이상
- 서비스 10개 이상
- Auto-scaling 필요성 증가 (피크 타임 트래픽 편차 심화)
- Multi-region 배포 필요

**현재 대안:**
- 개발 환경: `docker-compose-dev.yml` (인프라만 실행, 서비스는 로컬)
- 운영 환경: `docker-compose-prod.yml` (고정 인스턴스 수)
- 로드 밸런서: Nginx 별도 구성 (향후)

---

## 우선순위 요약

| 순위 | 문제 | 상태 | 영향도 | 조치 기한 |
|-----|------|------|--------|----------|
| ✅ | L1 캐시 불일치 | 해결됨 | - | 완료 |
| **P0** | Click 이벤트 중복 저장 | 미해결 | 🔴 데이터 정합성 | 즉시 |
| **P1** | Short Code 충돌 증가 | 모니터링 중 | 🟠 고부하 시 | 1개월 |
| **P1** | Connection Pool 미설정 | 미해결 | 🟠 스케일 제한 | 2주 |
| **P2** | Docker Compose 스케일 | 현상 유지 | 🟡 운영 불편 | 3개월 |

---

## 테스트 시나리오

### 1. Click 이벤트 중복 저장 테스트
```bash
# Kafka 메시지 수동 전송 (동일 eventId)
docker exec -it shortly-kafka kafka-console-producer \
  --bootstrap-server localhost:9092 \
  --topic url-clicked \
  --property "parse.key=true" \
  --property "key.separator=:"

# 같은 이벤트 2번 전송
key1:{"eventId":"test-123","eventType":"URL_CLICKED","timestamp":"2024-10-23T10:00:00Z","shortCode":"abc123","originalUrl":"https://example.com"}
key1:{"eventId":"test-123","eventType":"URL_CLICKED","timestamp":"2024-10-23T10:00:00Z","shortCode":"abc123","originalUrl":"https://example.com"}

# Click Service DB 확인
mysql -h localhost -P 3309 -u root -ppassword shortly_click
SELECT * FROM url_clicks WHERE short_code='abc123';

# 예상 결과 (현재): 2개 row (중복 저장)
# 목표 결과 (수정 후): 1개 row (멱등성)
```

### 2. Connection Pool 모니터링
```bash
# Prometheus 메트릭 확인
curl http://localhost:8081/actuator/prometheus | grep hikaricp

# MySQL 연결 수 확인
docker exec -it shortly-mysql-url mysql -uroot -ppassword \
  -e "SHOW STATUS LIKE 'Threads_connected';"
docker exec -it shortly-mysql-url mysql -uroot -ppassword \
  -e "SHOW VARIABLES LIKE 'max_connections';"
```

### 3. Multi-instance 부하 테스트
```bash
# 3개 인스턴스 실행
./gradlew :shortly-url-service:bootRun --args='--server.port=8081' &
./gradlew :shortly-url-service:bootRun --args='--server.port=8091' &
./gradlew :shortly-url-service:bootRun --args='--server.port=8101' &

# k6 부하 테스트
cd shortly-test/src/test/k6
k6 run end-to-end-load-test.js

# Short Code 충돌 재시도 메트릭 확인
curl http://localhost:8081/actuator/metrics/short.code.generation.retry
```
