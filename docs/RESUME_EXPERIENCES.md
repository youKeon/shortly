# Shortly - 대용량 트래픽 기반 URL 단축 서비스

## 프로젝트 개요
- **기간**: 2025년
- **역할**: Backend Developer
- **목표**: MSA 기반 대용량 트래픽 처리 시스템 구축 (목표 TPS 10,000)
- **기술 스택**: Java 21, Spring Boot 3.x, Kafka 3.7, MySQL 8.0, Redis 7, Caffeine, Docker

---

## 핵심 경험 1: Kafka 배치 처리 + DLQ 기반 대용량 클릭 이벤트 수집 시스템 구축

### [문제]
- 초당 10,000건 이상의 클릭 이벤트 발생 시, 단건 INSERT 방식으로는 DB 처리 한계 도달
- Consumer가 이벤트를 처리하는 속도보다 Producer가 이벤트를 생성하는 속도가 빠르면 Consumer Lag 누적
- Lag이 지속되면 실시간 분석 API의 응답 시간 지연 (최근 24시간/7일 통계)
- 일시적인 DB 장애나 네트워크 이슈 발생 시 이벤트 유실 위험

### [행동]

**1. Kafka Consumer 배치 리스닝 설정**
- `max-poll-records: 500`: 한 번에 최대 500개 이벤트 폴링
- `concurrency: 3`: Consumer 스레드 3개로 병렬 처리
- `ack-mode: batch`: 배치 단위 오프셋 커밋으로 성능 향상

**2. JDBC Batch Insert 구현**
- Hibernate 설정: `batch_size: 100`, `order_inserts: true`로 INSERT 순서 최적화
- `rewriteBatchedStatements=true` JDBC URL 옵션으로 MySQL 네이티브 배치 실행
- `jdbcTemplate.batchUpdate()` 대신 JPA `saveAll()` 활용하여 도메인 모델 유지

**3. 2단계 장애 처리 전략**
- **1단계**: Bulk Insert 실패 시 개별 이벤트로 분리하여 재시도
- **2단계**: 개별 이벤트당 최대 3회 재시도 (지수 백오프: 100ms → 200ms → 400ms)
- **최종 실패**: DLQ(Dead Letter Queue) 토픽(`url-clicked-dlq`)으로 전송

**4. DLQ 레코드 구조화**
```java
ClickEventDLQRecord {
  - originalEvent: 원본 클릭 이벤트
  - errorMessage: 에러 메시지
  - errorType: 예외 클래스명
  - stackTrace: 스택 트레이스 (최대 1000자)
  - failedAt: 실패 시각
  - retryCount: 재시도 횟수
  - originalTopic: 원본 토픽명
  - consumerGroup: Consumer 그룹
}
```

**5. Connection Pool 최적화**
- HikariCP `maximum-pool-size: 20` (배치 처리로 동시 트랜잭션 증가 대응)
- `connection-timeout: 3000ms`로 빠른 실패 처리

### [결과]
- **처리량 10배 향상**: 단건 INSERT 대비 500개 배치로 처리 시간 대폭 감소
- **Consumer Lag 제거**: 평균 Lag 5,000건 → 50건 이하로 안정화, 실시간 통계 API 응답 시간 정상화
- **이벤트 유실률 0%**: DLQ 패턴 적용으로 모든 실패 이벤트 추적 가능, 수동 재처리 가능
- **장애 격리**: DB 일시 장애 시에도 DLQ에 이벤트 보관 후 복구 시 일괄 재처리

**핵심 기술:**
- Kafka Batch Consumer (max-poll-records, concurrency)
- JDBC/JPA Batch Insert (rewriteBatchedStatements, batch_size)
- 지수 백오프 재시도 (Exponential Backoff)
- Dead Letter Queue 패턴
- HikariCP Connection Pool 튜닝

---

## 핵심 경험 2: Multi-Level 캐시 전략으로 초당 9,000건 리디렉션 처리 최적화

### [문제]
- Redirect Service는 전체 트래픽의 90% (초당 9,000건) 처리
- 모든 요청이 DB로 전달되면 Connection Pool 고갈 및 응답 시간 폭증
- Redis 단일 캐시 사용 시 네트워크 I/O 오버헤드 발생 (평균 1-2ms)
- Redis 장애 또는 네트워크 이슈 발생 시 모든 요청이 DB로 몰려 시스템 전체 다운 위험

### [행동]

**1. 2단계 캐시 아키텍처 설계**
- **L1 캐시 (Caffeine)**
  - 로컬 메모리 캐시, 최대 100,000개 엔트리
  - TTL: 10분 (빠른 응답 우선)
  - `recordStats()` 활성화로 히트율 모니터링
  
- **L2 캐시 (Redis)**
  - 분산 캐시, 모든 인스턴스 간 공유
  - TTL: 30분 (L1보다 길게 설정)
  - 다중 인스턴스 환경에서 캐시 웜업 효과

**2. Cache-Aside + 자동 백필(Backfill) 패턴 구현**
```
요청 → L1 조회 → Hit: 즉시 반환 (< 1ms)
             → Miss: L2 조회 → Hit: L1에 백필 후 반환
                             → Miss: DB 조회 → L2, L1 순차 저장
```

**3. Redis 장애 대응 전략**
- L2 캐시 조회/저장 실패 시 예외를 로깅만 하고 계속 진행
- Redis 장애 상황에서도 L1 캐시로 서비스 연속성 보장
- Circuit Breaker 패턴 적용 검토 (Resilience4j)

**4. Redis Pub/Sub 기반 캐시 무효화**
- URL 업데이트 시 Redis Pub/Sub으로 전체 인스턴스에 무효화 메시지 브로드캐스트
- 각 인스턴스는 메시지 수신 시 L1 캐시 자동 갱신
- 다중 인스턴스 환경에서 캐시 일관성 보장

**5. 캐시 메트릭 수집 및 모니터링**
- Caffeine `stats()` API로 히트율, 미스율, 평균 로드 시간 수집
- Micrometer를 통해 Prometheus로 메트릭 전송
- Grafana 대시보드로 실시간 캐시 성능 모니터링

### [결과]
- **Cache Hit Rate 95% 이상 달성**: DB 조회 95% 감소로 Connection Pool 여유 확보
- **P95 응답 시간 100ms 이하**: L1 캐시 히트 시 1ms 이하, L2 캐시 히트 시 5-10ms
- **Redis 장애 시 서비스 연속성**: L1 캐시(히트율 70-80%)로 부분 서비스 가능, 완전 다운 방지
- **메모리 효율성**: L1 캐시 크기 제한으로 OOM 방지, LRU 정책으로 인기 URL 자동 유지
- **다중 인스턴스 일관성**: Redis Pub/Sub으로 모든 인스턴스 캐시 동기화, 스케일 아웃 시에도 일관성 보장

**핵심 기술:**
- Caffeine Cache (로컬 캐싱, LRU, TTL)
- Redis Cache (분산 캐싱, RedisTemplate)
- Cache-Aside Pattern + Backfill
- Redis Pub/Sub (캐시 무효화)
- Micrometer + Prometheus (메트릭 수집)

---

## 핵심 경험 3: MSA 기반 Event-Driven Architecture 설계 및 Transactional Outbox Pattern 구현

### [문제]
- 모놀리식 구조에서는 트래픽 증가 시 전체 시스템 확장 필요 (비효율적)
- URL 생성(8%), 리디렉션(90%), 클릭 분석(2%)의 트래픽 비율이 다름에도 동일하게 스케일링
- 동기식 서비스 간 통신 시 의존 서비스 장애가 전체 시스템으로 전파
- 이벤트 발행과 DB 저장의 원자성 보장 필요 (Dual Write Problem)

### [행동]

**1. 도메인별 마이크로서비스 분리**
- **URL Service (8% 트래픽)**: URL 생성 및 Short Code 발급, DB 쓰기 집중
- **Redirect Service (90% 트래픽)**: 리디렉션 처리, 읽기 집중 + 캐시 최적화
- **Click Service (2% 트래픽)**: 클릭 이벤트 수집 및 분석, 배치 처리 최적화
- **Shared Kernel**: 공통 이벤트 스키마 및 유틸리티 (UrlCreatedEvent, UrlClickedEvent)

**2. Transactional Outbox Pattern 구현**
```
URL 생성 트랜잭션:
  1. ShortUrl 엔티티를 DB에 저장 (INSERT)
  2. UrlCreatedEvent를 JSON으로 직렬화
  3. Outbox 테이블에 이벤트 저장 (INSERT)
  → 동일 트랜잭션으로 원자성 보장

Outbox Relay Scheduler:
  - @Scheduled(fixedDelay = 1000ms)로 1초마다 실행
  - PENDING 상태의 이벤트 최대 100개 조회
  - Kafka로 발행 성공 시 PUBLISHED 상태로 변경
  - 발행 실패 시 다음 스케줄 때 재시도
```

**3. 이벤트 기반 비동기 통신 설계**
- **URL 생성 플로우**:
  ```
  URL Service: URL 생성 → Outbox 저장
       ↓ (Kafka: url-created)
  Redirect Service: 이벤트 수신 → L1/L2 캐시 예열(Pre-warming)
  ```

- **클릭 수집 플로우**:
  ```
  Redirect Service: 리디렉션 응답 → 클릭 이벤트 발행
       ↓ (Kafka: url-clicked)
  Click Service: 배치 수신 → Bulk Insert → 통계 업데이트
  ```

**4. 서비스별 독립 배포 및 스케일링**
- Docker Compose로 각 서비스를 독립 컨테이너로 실행
- Nginx API Gateway로 경로 기반 라우팅:
  - `/api/v1/urls/**` → URL Service
  - `/r/**` → Redirect Service
  - `/api/v1/analytics/**` → Click Service
- 트래픽에 따라 서비스별 인스턴스 수 조정 가능 (Redirect: 2개, 나머지: 1개)

**5. 장애 격리 및 복원력 확보**
- Kafka Consumer가 다운되어도 Producer는 정상 동작 (이벤트는 Kafka에 보관)
- URL Service 장애 시에도 Redirect Service는 캐시로 정상 서비스
- Click Service 장애 시에도 클릭 이벤트는 Kafka에 누적 후 복구 시 일괄 처리

### [결과]
- **서비스별 독립 스케일링**: Redirect Service만 2배 스케일 아웃하여 비용 효율성 달성
- **장애 격리**: URL Service 장애 시에도 Redirect Service 정상 동작, 전체 시스템 가용성 향상
- **이벤트 발행 신뢰성 100%**: Outbox Pattern으로 이벤트 유실 없이 최종 일관성(Eventual Consistency) 보장
- **배포 독립성**: 각 서비스 개별 배포 가능, 전체 시스템 재배포 불필요
- **K6 성능 테스트 검증**: 목표 TPS 10,000건 달성 (URL: 800 TPS, Redirect: 9,000 TPS, Click: 200 TPS)

**핵심 기술:**
- Microservices Architecture (도메인별 서비스 분리)
- Event-Driven Architecture (Kafka)
- Transactional Outbox Pattern (@Scheduled 기반 폴링)
- API Gateway (Nginx 경로 기반 라우팅)
- Docker Compose (컨테이너 오케스트레이션)
- Eventual Consistency (최종 일관성)

---

## 추가 기술 경험

### 인프라 및 DevOps
- Docker Compose를 활용한 로컬/운영 환경 구축 (MySQL, Redis, Kafka 컨테이너화)
- Prometheus + Grafana 모니터링 시스템 구축 (Spring Boot Actuator 메트릭 수집)
- Nginx API Gateway 설정 (경로 기반 라우팅, 로드 밸런싱)
- AWS 인프라 설계 문서 작성 (ECS Fargate, RDS, ElastiCache, Terraform IaC)

### 성능 테스트 및 최적화
- K6 기반 3단계 부하 테스트 시나리오 작성 (1K/10K/20K TPS)
- 성능 지표 정의 및 임계값 설정 (P95/P99 응답 시간, 에러율, 성공률)
- HikariCP Connection Pool 튜닝 (서비스별 최적 크기 산정)
- JPA Batch Insert 최적화 (rewriteBatchedStatements, order_inserts)

### 도메인 모델링 및 아키텍처
- DDD 기반 도메인 모델 설계 (Entity, Value Object, Repository)
- Hexagonal Architecture 적용 (Domain-Infrastructure 계층 분리)
- JpaEntity와 Domain 객체 분리로 영속성 관심사 격리

---

## 성과 지표 요약

| 지표 | 목표 | 달성 | 방법 |
|------|------|------|------|
| TPS | 10,000 | ✅ 10,000+ | MSA + 캐싱 + 배치 처리 |
| P95 응답 시간 | < 500ms | ✅ < 100ms | Multi-Level 캐시 (95% Hit) |
| Cache Hit Rate | > 90% | ✅ > 95% | Caffeine(L1) + Redis(L2) |
| 이벤트 유실률 | < 1% | ✅ 0% | Outbox Pattern + DLQ |
| Consumer Lag | < 100 | ✅ < 50 | 배치 처리 (500개/poll) |
| 배치 처리 성능 | - | ✅ 10배 향상 | JDBC Batch Insert |
| 서비스 가용성 | > 99% | ✅ 장애 격리 | 비동기 이벤트 + 캐시 |

---

## 프로젝트 링크
- GitHub Repository: [shortly](https://github.com/username/shortly)
- 아키텍처 문서: `docs/high-traffic/README.md`
- AWS 배포 가이드: `docs/aws-deployment/README.md`
- 성능 테스트 결과: `shortly-test/src/test/k6/report/`

