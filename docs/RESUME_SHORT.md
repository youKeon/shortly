# Shortly 프로젝트 - 이력서 작성용

## 1. Kafka 배치 처리로 클릭 이벤트 처리량 10배 향상

**[문제]** 초당 10,000건 이벤트 발생 시 단건 INSERT로 DB 병목, Consumer Lag 5,000건 누적으로 실시간 API 지연.

**[행동]** 500개 배치 리스닝 + JDBC Batch Insert 적용, DLQ(실패 이벤트 큐) + 지수 백오프 재시도 구현.

**[결과]** 처리량 10배 향상(1건 → 500건 배치), Consumer Lag 99% 감소(5,000 → 50건), 이벤트 유실률 0%.

---

## 2. Caffeine + Redis 2단 캐시로 응답 시간 95% 단축

**[문제]** 초당 9,000건 요청 시 DB 직접 조회로 응답 시간 2초, Redis 장애 시 시스템 전체 다운.

**[행동]** L1 로컬 캐시(Caffeine) + L2 분산 캐시(Redis) 구조 설계, L1 미스 시 L2 자동 백필.

**[결과]** P95 응답 시간 95% 단축(2,000ms → 100ms), 캐시 히트율 95% 달성(0% → 95%), DB 부하 95% 감소.

---

## 3. Outbox Pattern으로 38만 건 이벤트 유실률 0% 달성

**[문제]** DB 저장과 Kafka 발행을 분리 시 Dual Write로 데이터 불일치, 네트워크 장애 시 이벤트 유실.

**[행동]** DB + Outbox 테이블을 단일 트랜잭션으로 묶음, 1초마다 미발행 이벤트 폴링 후 Kafka 재발행.

**[결과]** 38만 건 이벤트 유실 0%, 폴링 성능 94% 향상(3초 → 180ms), Kafka 장애 시 자동 재시도.

---

## 기술 스택
Java 21, Spring Boot 3.x, Kafka 3.7, MySQL 8.0, Redis 7, Caffeine, Docker, Prometheus, Grafana, K6, Nginx

