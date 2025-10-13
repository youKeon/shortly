# 성능 테스트 개요

## 테스트 목표

**대규모 트래픽 처리를 위한 아키텍처 최적화 검증**

- 10,000 TPS 이상 처리 가능한 시스템 구축
- 각 Phase별 성능 개선 효과 정량적 측정
- 캐싱, 비동기 처리, Reactive 아키텍처의 실제 효과 검증

---

## Phase별 구현 내용

### Phase 1: 기본 구성 (No Cache)

**아키텍처**
```
Client → Tomcat → JPA → MySQL
```

**핵심 구현**
- Spring Boot + Tomcat (Thread-based)
- JPA (Hibernate)
- MySQL (단일 인스턴스)
- 캐시 없음

**특징**
- 모든 요청이 DB 직행
- Blocking I/O 모델
- 가장 단순한 구성

---

### Phase 2: Redis 캐싱

**아키텍처**
```
Client → Tomcat → Redis Cache → MySQL
```

**핵심 구현**
- Redis 캐시 추가 (`@Cacheable`)
- TTL: 10분
- HikariCP: max 50 connections

**특징**
- 리디렉션 요청의 캐시 히트율 향상
- 네트워크 기반 글로벌 캐시
- DB 부하 감소

---

### Phase 3: 비동기 이벤트 처리

**아키텍처**
```
Client → Tomcat → Redis Cache → MySQL
                     ↓
                  Kafka (클릭 이벤트)
```

**핵심 구현**
- Kafka 이벤트 스트리밍
- 비동기 클릭 카운트 처리
- Redis 버퍼링 + 스케줄러 배치 저장

**특징**
- DB 쓰기 부하 분산
- 이벤트 기반 아키텍처
- API 응답 시간 단축

---

### Phase 4: Reactive 아키텍처

**아키텍처**
```
Client → Netty (WebFlux) → Caffeine (L1) → Redis (L2) → MySQL
```

**핵심 구현**
- Spring WebFlux (Reactive)
- 2-Level Caching (Caffeine + Redis)
- Non-blocking I/O
- Reactive Redis

**특징**
- Event Loop 기반 높은 동시성
- 로컬 캐시로 초고속 응답
- 완전 비동기 처리

---

## 테스트 결과 요약

### 성능 비교

| Phase | 아키텍처 | TPS | 목표 달성 | P95 | 평균 응답 |
|-------|---------|-----|----------|-----|----------|
| Phase 1 | Tomcat (No Cache) | 4,198 | 42.0% | 430.43ms | 196.15ms |
| Phase 2 | Tomcat + Redis | 4,587 | 45.9% | 425.18ms | 179.5ms |
| Phase 3 | Tomcat + Redis + Kafka | 4,940 | 49.4% | 360.56ms | 166.87ms |
| Phase 4 | WebFlux + 2-Level Cache | **10,781** | **107.8%** | 579.87ms | **65.97ms** |

### Phase별 개선율 (Phase 1 기준)

| Phase | TPS 개선 | 평균 응답 개선 | 핵심 성과 |
|-------|---------|--------------|----------|
| Phase 2 | +9.3% | -8.5% | Redis 캐싱 효과 |
| Phase 3 | +17.7% | -14.9% | 비동기 처리 효과 |
| Phase 4 | **+157%** | **-66.4%** | **Reactive 아키텍처** |

---

## 핵심 발견

### 1. 캐싱의 중요성
- Phase 1 → Phase 2: Redis 추가만으로 9.3% TPS 향상
- Phase 4: 2-Level Cache로 중간값 0.876ms 달성

### 2. Blocking I/O의 한계
- Phase 1~3: Tomcat 기반으로는 10K TPS 불가능
- 최대 4,940 TPS (목표의 49.4%)
- 스레드 풀 병목 현상

### 3. Reactive의 필요성
- Phase 4: WebFlux로 10K TPS 돌파
- Non-blocking I/O로 소수 스레드가 높은 동시성 처리
- Event Loop 기반 효율적 리소스 활용

### 4. 로컬 캐시의 효과
- Caffeine L1 캐시: 약 1μs 응답
- Redis L2 캐시: 약 1-2ms 응답
- 90% 리디렉션 요청이 Caffeine에서 즉시 처리

---

## 테스트 방법

### 테스트 도구
- k6 (부하 테스트)
- 목표: 10,000 TPS
- 최대 VU: 1,200
- 테스트 시간: 3분

### 트래픽 패턴
- URL 단축: 10%
- 리디렉션: 90%
- Warmup: 200개 shortCode 사전 생성

### 각 Phase 테스트 실행

```bash
# Phase 1
git checkout phase1
cd backend
DB_USERNAME=root DB_PASSWORD=<password> ./gradlew bootRun --args='--spring.profiles.active=phase1'
k6 run backend/performance-tests/phase1/target-10k-test.js

# Phase 2
git checkout phase2
cd backend
DB_USERNAME=root DB_PASSWORD=<password> ./gradlew bootRun --args='--spring.profiles.active=phase2'
k6 run backend/performance-tests/phase2/target-10k-test.js

# Phase 3
git checkout phase3
cd backend
DB_USERNAME=root DB_PASSWORD=<password> ./gradlew bootRun --args='--spring.profiles.active=phase3'
k6 run backend/performance-tests/phase3/target-10k-test.js

# Phase 4
git checkout phase4
cd backend
DB_USERNAME=root DB_PASSWORD=<password> ./gradlew bootRun --args='--spring.profiles.active=phase4'
k6 run backend/performance-tests/phase4/target-10k-test.js
```

---

## 결론

**10K TPS 달성을 위한 필수 요소**

1. Reactive 아키텍처 (WebFlux, Netty)
2. 2-Level Caching (Caffeine + Redis)
3. Non-blocking I/O
4. 비동기 이벤트 처리

**Phase 4에서만 목표 달성 가능**
- Tomcat 기반 (Phase 1~3): 최대 4,940 TPS
- WebFlux 기반 (Phase 4): 10,781 TPS

대규모 트래픽 처리는 아키텍처 선택의 문제이며, Reactive + 2-Level Cache가 필수 전략임을 실증적으로 검증했습니다.

---

**문서 작성일**: 2025-10-13  
**테스트 스크립트**: target-10k-test.js (1,200 VU, 3분)  
**상세 결과**: 각 Phase 디렉토리의 TEST_RESULTS.md 참조
