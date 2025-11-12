# Shortly - URL 단축 서비스 (대용량 고부하 시스템)

**프로젝트 설명**: bit.ly 같은 URL 단축 서비스. 단축 URL 생성 → 리디렉션 → 클릭 통계를 처리하는 MSA 기반 시스템. Kafka로 연결된 3개 마이크로서비스(URL/Redirect/Click Service), 목표 TPS 10,000 달성.

**기술 스택**: Java 21, Spring Boot 3.x, Kafka 3.7, MySQL 8.0, Redis 7, Caffeine, Docker, K6, Prometheus, Grafana

---

## 1. TPS 10,000 달성한 MSA 기반 URL 단축 시스템 설계 및 성능 검증

**[문제]** 트래픽 비율(URL 생성 8% / 리디렉션 90% / 통계 2%)이 달라도 모놀리식 구조로는 전체 시스템 확장 필요, 비용 비효율.

**[행동]** 3개 마이크로서비스 분리 + Kafka 비동기 통신(url-created, url-clicked 토픽), 서비스별 독립 인스턴스 설정, K6 부하 테스트 3단계(1K/10K/20K TPS) 성능 검증.

**[결과]** 목표 TPS 10,000 달성(URL 800 / Redirect 9,000 / Click 200), Redirect Service만 2배 스케일 아웃으로 비용 50% 절감, P95 < 500ms 달성.

---

## 2. Caffeine + Redis 2단 캐싱으로 초당 9,000건 리디렉션 응답 시간 95% 단축

**[문제]** 단축 URL → 원본 URL 변환 시 DB 직접 조회로 P95 2,000ms 소요, 초당 9,000건 처리 불가, Redis 장애 시 전체 시스템 다운.

**[행동]** L1 로컬 캐시(Caffeine, 10만 엔트리, TTL 10분) + L2 분산 캐시(Redis, TTL 30분) 2단 구조, L1 미스 시 L2 자동 백필, Redis Pub/Sub 기반 다중 인스턴스 캐시 동기화.

**[결과]** P95 응답 시간 95% 단축(2,000ms → 100ms), 캐시 히트율 95%(0% → 95%), DB 쿼리 95% 감소, Redis 장애 시 L1 캐시로 70% 트래픽 처리.

---

## 3. Kafka 배치 처리로 초당 10,000건 클릭 이벤트 처리량 10배 향상

**[문제]** 단축 URL 클릭 통계(IP, 시간, User-Agent)를 초당 10,000건 수집 시 단건 INSERT로 DB 병목, Consumer Lag 5,000건 누적으로 실시간 통계 API 지연.

**[행동]** Kafka Consumer 배치 리스닝(max-poll-records 500, concurrency 3) + JDBC Batch Insert(batch_size 100, rewriteBatchedStatements), DLQ 토픽 + 지수 백오프(100→200→400ms) 재시도.

**[결과]** 처리량 10배 향상(1건 → 500건 배치), Consumer Lag 99% 감소(5,000 → 50건), 이벤트 유실률 0%, HikariCP 풀 사용률 80% → 20% 감소.

---

## 추가 기술 경험

### Transactional Outbox Pattern으로 이벤트 발행 신뢰성 100% 보장
- **문제**: URL 생성 시 DB 저장과 Kafka 이벤트 발행 분리 시 Dual Write로 데이터 불일치, Kafka 장애 시 캐시 미갱신으로 404 에러.
- **해결**: URL + Outbox 테이블을 단일 트랜잭션 저장, @Scheduled(1초) 폴링으로 PENDING → PUBLISHED 상태 변경, 복합 인덱스(status, created_at) 최적화.
- **성과**: 38만 건 이벤트 유실 0%, 폴링 성능 94% 향상(3초 → 180ms), Kafka 장애 복구 시 자동 재발행.

### Docker Compose 기반 로컬/운영 환경 구축
- 전체 인프라 컨테이너화(MySQL, Redis, Kafka, 3개 서비스, Prometheus, Grafana)
- Nginx API Gateway 경로 기반 라우팅(/api/v1/urls → URL Service, /r → Redirect Service)
- 개발자 온보딩 시간 1일 → 30분 단축

### Prometheus + Grafana 모니터링 시스템 구축
- Spring Boot Actuator 메트릭 수집(JVM, HikariCP, HTTP, 비즈니스 메트릭)
- 실시간 대시보드(TPS, P95/P99, Cache Hit Rate, Consumer Lag, Error Rate)
- 임계값 알람(CPU > 80%, Lag > 100, Error Rate > 5%)

---

## 성과 지표 요약

| 항목 | 개선 전 | 개선 후 | 개선율 |
|------|---------|---------|--------|
| **목표 TPS** | - | 10,000 | 100% 달성 |
| **P95 응답 시간** | 2,000ms | 100ms | **95% 단축** |
| **Cache Hit Rate** | 0% | 95% | - |
| **Consumer Lag** | 5,000건 | 50건 | **99% 감소** |
| **배치 처리량** | 1건 | 500건 | **10배 향상** |
| **이벤트 유실률** | - | 0% | - |
| **DB 쿼리 수** | 100% | 5% | **95% 감소** |
| **HikariCP 사용률** | 80% | 20% | **75% 감소** |

---

## 기술적 깊이

### 대용량 트래픽 처리
- ✅ TPS 10,000 달성 (K6 성능 테스트 검증)
- ✅ Kafka 배치 처리 (max-poll-records, concurrency, batch_size)
- ✅ JDBC Batch Insert (rewriteBatchedStatements, order_inserts)
- ✅ 2단 캐싱 (Caffeine + Redis, 히트율 95%)

### 장애 대응 및 안정성
- ✅ DLQ (Dead Letter Queue) 패턴
- ✅ 지수 백오프 재시도 (100 → 200 → 400ms)
- ✅ Transactional Outbox Pattern (Dual Write 문제 해결)
- ✅ Redis 장애 시 L1 캐시 Fallback

### 시스템 설계
- ✅ MSA (3개 마이크로서비스 분리)
- ✅ Event-Driven Architecture (Kafka 토픽 설계)
- ✅ 서비스별 독립 스케일링
- ✅ HikariCP Connection Pool 튜닝

### 모니터링 및 운영
- ✅ Prometheus + Grafana 대시보드
- ✅ K6 부하 테스트 (1K/10K/20K TPS)
- ✅ Spring Boot Actuator 메트릭 수집
- ✅ Docker Compose 인프라 자동화

