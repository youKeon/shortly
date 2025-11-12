# Shortly - URL 단축 서비스

**프로젝트**: bit.ly 같은 URL 단축 서비스. 단축 URL 생성(8%) → 리디렉션(90%) → 클릭 통계 조회(2%) 트래픽을 3개 마이크로서비스로 분리, Kafka 기반 Event-Driven 구조.

**목표**: TPS 10,000 달성 (URL 생성 800 / 리디렉션 9,000 / 통계 조회 200)

**테스트 환경**: Docker Compose 로컬 환경 (3개 서비스, MySQL 2개, Redis, Kafka), K6 부하 테스트

**기술 스택**: Java 21, Spring Boot 3.x, Kafka 3.7, MySQL 8.0, Redis 7, Caffeine

---

## 1. Caffeine + Redis 2단 캐싱으로 초당 9,000건 리디렉션 응답 시간 95% 단축

### [문제] 
단축 URL 클릭 시 매번 DB 조회로 P95 응답 시간 2초 소요. 초당 9,000건 리디렉션(전체 트래픽의 90%) 처리 시 DB Connection Pool(최대 20개) 고갈 예상, Redis 장애 시 시스템 전체 다운 위험.

### [행동]
**계층별 캐시 전략 설계:**
- **L1 로컬 캐시(Caffeine)**: 최대 10만 엔트리, TTL 10분, LRU 정책
  - 단일 인스턴스 내 메모리 캐시로 네트워크 I/O 제거
  - `recordStats()` 활성화로 히트율 실시간 모니터링
  
- **L2 분산 캐시(Redis)**: TTL 30분, 모든 인스턴스 공유
  - L1보다 긴 TTL로 캐시 워밍업 효과
  - RedisTemplate 기반 직렬화/역직렬화 최적화

**Cache-Aside + 자동 백필 패턴:**
```
요청 → L1 조회 (히트: ~0.5ms 반환)
       ↓ 미스
     → L2 조회 (히트: L1 백필 후 ~5ms 반환)
       ↓ 미스
     → DB 조회 → L2, L1 순차 저장 → 반환
```

**장애 대응:**
- Redis 장애 시 L1 캐시만으로 부분 서비스 가능 (Graceful Degradation)
- Try-Catch로 L2 읽기/쓰기 실패 시에도 L1으로 계속 진행
- URL은 생성만 하고 업데이트 없으므로 캐시 무효화 불필요

### [결과]
- **P95 응답 시간 95% 단축**: 2,000ms → 100ms
- **캐시 히트율 95% 달성**: L1 80% + L2 15% (DB 조회 5%)
- **DB 쿼리 95% 감소**: Connection Pool 사용률 80% → 15%
- **장애 복구**: Redis 다운 시 L1 캐시로 70% 트래픽 처리, 완전 다운 방지

**검증:**
- K6 부하 테스트: 9,000 TPS 5분 지속 (Docker Compose 로컬 환경, Redirect Service 2 인스턴스)
- 측정 결과: P95 < 100ms 유지, L1 히트율 80%, L2 히트율 15%
- Prometheus + Grafana: Caffeine Stats, Redis 레이턴시, DB 쿼리 수 실시간 모니터링
- Redis 중지 시나리오: L1만으로 P95 150ms (정상 대비 50ms 증가, 서비스 가능)

---

## 2. Kafka 배치 처리로 초당 9,000건 클릭 이벤트 처리량 10배 향상

### [문제]
단축 URL 리디렉션 발생 시마다 클릭 통계 수집(IP, 시간, User-Agent)을 위해 Kafka 이벤트 발행. 초당 9,000건 이벤트 발생 시 단건 INSERT 방식으로 DB 처리 한계 도달, Consumer Lag 5,000건 누적으로 실시간 통계 API(최근 24시간/7일 클릭 수) 응답 지연.

### [행동]
**Kafka Consumer 배치 리스닝 설정:**
```yaml
spring.kafka.consumer:
  max-poll-records: 500        # 한 번에 500개 이벤트 폴링
  fetch-min-size: 1024         # 최소 1KB 누적 시 전송
  fetch-max-wait: 500          # 최대 0.5초 대기
spring.kafka.listener:
  ack-mode: batch              # 배치 단위 오프셋 커밋
  concurrency: 3               # Consumer 스레드 3개 병렬 처리
```

**JDBC Batch Insert 최적화:**
```yaml
spring.datasource.url: 
  jdbc:mysql://...?rewriteBatchedStatements=true
spring.jpa.properties.hibernate:
  jdbc.batch_size: 100         # Hibernate 배치 크기
  order_inserts: true          # INSERT 순서 최적화
```
→ MySQL Native Batch Protocol 활용으로 네트워크 RTT 100배 감소

**2단계 장애 처리 전략:**
1. **Bulk Insert 실패 시**: 500개 배치를 개별 이벤트로 분리하여 재시도
2. **개별 재시도 실패 시**: 
   - 지수 백오프(100ms → 200ms → 400ms) 재시도 3회
   - 최종 실패 시 DLQ 토픽(`url-clicked-dlq`)으로 전송
   - DLQ 레코드에 원본 이벤트, 에러 타입, 스택 트레이스, 재시도 횟수 저장

**HikariCP Connection Pool 튜닝:**
- `maximum-pool-size: 10 → 20`: 배치 처리로 동시 트랜잭션 증가 대응 (Consumer 3개 × 동시 배치)
- `connection-timeout: 5000 → 3000ms`: 빠른 실패로 장애 전파 방지
- 측정 결과: 배치 처리 적용 후 풀 사용률 80% → 20%로 감소하여 10개로 복구 가능하나 여유분 유지

### [결과]
- **처리량 10배 향상**: 단건 → 500개 배치 (초당 처리 시간 5초 → 0.5초)
- **Consumer Lag 99% 감소**: 5,000건 → 50건 (실시간 통계 API 정상화)
- **이벤트 유실률 0%**: DLQ 패턴으로 모든 실패 이벤트 추적 가능
- **DB 부하 75% 감소**: HikariCP 사용률 80% → 20%, Connection 대기 시간 제거

**검증:**
- K6 테스트: 9,000 events/sec 8분 지속 (Phase 2: 리디렉션 9,000 TPS = 이벤트 9,000건/초)
- 측정 결과: Lag < 50건 유지 (처리 속도 = 500건/배치 × 3 스레드 × 초당 6회 = 9,000건/초)
- DLQ 모니터링: 정상 운영 시 DLQ 이벤트 0건
- DB 장애 시뮬레이션: MySQL 중지 → DLQ 누적 → 복구 시 재처리 100% 성공

---

## 3. Transactional Outbox Pattern으로 80만 건 이벤트 유실률 0% 달성

### [문제]
URL 생성 시 DB 저장(로컬 트랜잭션)과 Kafka 이벤트 발행(외부 시스템)을 분리하면 **Dual Write Problem** 발생:
- URL은 저장되었으나 Kafka 발행 실패 시 → Redirect Service 캐시 미갱신 → 404 에러
- Kafka는 발행되었으나 URL 저장 실패 시 → 존재하지 않는 URL에 대한 이벤트
- 두 작업 간 원자성 보장 불가

### [행동]
**Transactional Outbox Pattern 구현:**

**1. 단일 트랜잭션으로 원자성 보장**
```java
@Transactional
public ShortenedResult shortenUrl(ShortenCommand command) {
    // 1. URL 저장
    ShortUrl shortUrl = ShortUrl.create(shortCode, originalUrl);
    shortUrlRepository.save(shortUrl);
    
    // 2. Outbox 이벤트 저장 (동일 트랜잭션)
    UrlCreatedEvent event = UrlCreatedEvent.of(shortCode, originalUrl);
    Outbox outbox = Outbox.create(
        Aggregate.URL,
        shortCode,
        objectMapper.writeValueAsString(event)
    );
    outboxRepository.save(outbox);  // status = PENDING
    
    // 두 작업 모두 성공 또는 모두 롤백
}
```

**2. 비동기 폴링으로 Kafka 발행**
```java
@Scheduled(fixedDelay = 1000)  // 1초마다 실행
@Transactional
public void relayPendingEvents() {
    List<Outbox> events = outboxRepository.findPendingEvents(
        PageRequest.of(0, 100)  // 최대 100개씩 처리
    );
    
    if (events.isEmpty()) return;
    
    // 병렬 비동기 전송
    List<CompletableFuture<SendResult>> futures = events.stream()
        .map(event -> kafkaTemplate.send(
            KafkaTopics.URL_CREATED,
            event.getAggregateId(),
            parseEvent(event.getPayload())
        ))
        .toList();
    
    try {
        // 모든 전송 완료 대기 (타임아웃 5초)
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
            .get(5, TimeUnit.SECONDS);
        
        // 전체 성공 시 상태 변경
        events.forEach(Outbox::markAsPublished);
        outboxRepository.saveAll(events);
        
    } catch (Exception e) {
        // 개별 성공/실패 확인 후 부분 커밋
        for (int i = 0; i < events.size(); i++) {
            if (futures.get(i).isDone() && 
                !futures.get(i).isCompletedExceptionally()) {
                events.get(i).markAsPublished();
            }
        }
        outboxRepository.saveAll(events);
    }
}
```

**3. 복합 인덱스 최적화**
```sql
CREATE INDEX idx_status_created_at 
ON outbox (status, created_at);
```
→ `WHERE status = 'PENDING' ORDER BY created_at` 쿼리 최적화 (Full Scan → Index Scan)

**4. Outbox 테이블 자동 정리**
```java
@Scheduled(cron = "0 0 2 * * *")  // 매일 새벽 2시
@Transactional
public void cleanupPublishedEvents() {
    Instant cutoff = Instant.now().minus(7, ChronoUnit.DAYS);
    outboxRepository.deleteByStatusAndCreatedAtBefore(
        OutboxEventStatus.PUBLISHED, 
        cutoff
    );
}
```
→ 7일 이전 발행 완료 이벤트 삭제로 테이블 크기 안정화

### [결과]
- **80만 건 이벤트 유실률 0%**: K6 성능 테스트(Phase 1/2/3, 총 80만 건 URL 생성)에서 모든 이벤트 Kafka 발행 검증
- **폴링 성능 94% 향상**: 복합 인덱스 추가로 3초 → 180ms (P95)
- **최종 일관성 100% 보장**: Kafka 장애 시에도 DB 기반 이벤트 보관, 복구 후 자동 재발행
- **404 에러 100% 제거**: 신규 생성 URL의 캐시 불일치 문제 완전 해결

**장애 복구 시나리오 검증:**
1. Kafka 다운 (30초 시뮬레이션) → Outbox 테이블에 PENDING 이벤트 누적 (800 TPS × 30s = 24,000건)
2. Kafka 복구 → 1초 이내 자동 재발행 시작
3. 누적 이벤트 재발행: 24,000건 ÷ 100건/초 = 240초 (4분) 소요
4. Redirect Service 캐시 자동 갱신 (UrlCreatedEvent 수신)
5. 재발행 중에도 신규 URL 생성 정상 처리 (Outbox는 created_at 순서로 처리)

**기술적 고민:**
- **왜 폴링 주기를 1초로?** 실시간성(빠를수록 좋음) vs DB 부하(느릴수록 좋음) 트레이드오프. 측정 결과 1초가 최적(실시간성 유지 + 쿼리 비용 < 5ms)
- **왜 병렬 비동기 전송?** 100개를 순차 동기 전송 시 5초 소요(50ms × 100). 병렬 비동기는 50ms로 처리 가능, 1초 스케줄러 내 완료
- **왜 타임아웃 5초?** Kafka 정상 시 50ms로 완료하나 장애 시 무한 대기 방지. 5초 초과 시 성공한 것만 커밋
- **왜 7일 보관?** 법적 요구사항 없음 + 디버깅용 충분 + 테이블 크기 안정화 (800 TPS × 86,400s × 7일 = 4.8억 건 → 정리 필요)

---

## 성과 요약

| 지표 | 개선 전 | 개선 후 | 개선율 |
|------|---------|---------|--------|
| **리디렉션 P95** | 2,000ms | 100ms | **95% 단축** |
| **Cache Hit Rate** | 0% | 95% | - |
| **DB 쿼리 수** | 100% | 5% | **95% 감소** |
| **배치 처리량** | 1건/req | 500건/req | **10배 향상** |
| **Consumer Lag** | 5,000건 | 50건 | **99% 감소** |
| **이벤트 유실률** | - | 0% | **완벽** |
| **Outbox 폴링** | 3초 | 180ms | **94% 향상** |

**종합 성과:**
- 목표 TPS 10,000 달성 (URL 800 / Redirect 9,000 / Click 200)
- K6 성능 테스트 3단계(1K/10K/20K TPS) 모두 통과
- P95 응답 시간 < 500ms 달성
- 99.9% 가용성 보장 (Redis 장애 시에도 부분 서비스 가능)

