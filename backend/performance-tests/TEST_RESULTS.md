# Phase 2 테스트 결과 (10K TPS 목표)

## 테스트 환경
- **아키텍처**: Spring Boot (Tomcat) + Redis Cache
- **테스트 도구**: k6
- **목표 TPS**: 10,000+
- **최대 VU**: 1,200
- **테스트 시간**: 3분

---

## 📊 테스트 결과

| 지표 | 결과 | 목표 | 달성 여부 |
|------|------|------|-----------|
| **TPS** | **4,587** | 10,000+ | ❌ **45.9%** |
| **P95 응답 시간** | **425.18ms** | <200ms | ❌ |
| **평균 응답 시간** | **179.5ms** | - | - |
| **중간값 응답 시간** | **152.75ms** | - | - |
| **에러율** | **0%** | <5% | ✅ |
| **리디렉션 성공률** | **100%** | >95% | ✅ |
| **단축 성공률** | **100%** | >95% | ✅ |
| **총 요청 수** | **827,486** | - | - |

---

## 아키텍처

```
Client Request
    ↓
Tomcat (Thread Pool: max 500)
    ↓
ShortUrlService (Blocking)
    ↓
Redis Cache (L1) ← 모든 조회가 네트워크 거침
    ↓ (Cache Miss)
MySQL DB (JPA)
```

### 주요 구성
- **Web Server**: Tomcat (Thread-based, Blocking I/O)
- **Cache**: Redis (글로벌 캐시, 네트워크 I/O)
- **DB Connection Pool**: HikariCP (max: 50)

---

## 성능 분석

### 🔴 병목 지점

#### 1. **Tomcat 스레드 풀 한계**
```yaml
server:
  tomcat:
    threads:
      max: 500  # 최대 500개 스레드
```

**문제점:**
- 1,200 VU (동시 사용자)를 500 스레드로 처리
- 스레드 부족 시 요청 큐잉 → 대기 시간 증가
- Blocking I/O 모델의 근본적 한계

#### 2. **Redis 네트워크 오버헤드**
```java
// 모든 리디렉션 요청이 Redis 네트워크 호출
String cachedUrl = redisTemplate.opsForValue().get(shortCode); // ~1-2ms
```

**문제점:**
- 90% 리디렉션 요청이 Redis 거침
- 로컬 메모리 캐시 없음
- 네트워크 I/O 지연 누적

#### 3. **Blocking I/O 누적**
- Redis 조회: Blocking
- DB 조회: Blocking
- 스레드가 I/O 대기 중 다른 작업 불가

---

### 📈 TPS가 10K에 미달한 이유

```
요청 → Tomcat 스레드 할당 → Redis 대기 → DB 대기 → 응답
        ↑ 병목 1              ↑ 병목 2      ↑ 병목 3
```

1. **스레드 풀 포화** (500 스레드 vs 1,200 VU)
2. **Blocking I/O 대기** (Redis 1-2ms × 대량 요청)
3. **네트워크 오버헤드** (로컬 캐시 없음)

**결과:**
- TPS: 4,587 (목표의 45.9%)
- P95: 425.18ms (목표의 2배 초과)

---

## 상세 메트릭

### HTTP 요청
```
총 요청 수:        827,486
TPS:              4,587/s
평균 응답 시간:    179.5ms
중간값:           152.75ms
P90:              345.42ms
P95:              425.18ms
최대:             1.87s
```

### 트래픽 분포
```
리디렉션 요청:     744,982 (90%)
단축 요청:         82,304 (10%)
```

### 성공률
```
전체 성공률:       100%
리디렉션 성공률:   100%
단축 성공률:       100%
에러율:           0%
```

---

## 개선 방안

### 1️⃣ **로컬 캐시 추가** (Caffeine)
```java
// L1: Caffeine (로컬 메모리) - 초고속
// L2: Redis (글로벌 캐시) - 일관성
```
**예상 효과:**
- 평균 응답 시간: 179.5ms → **<50ms** (72% 개선)
- TPS: 4,587 → **7,000+** (52% 증가)

### 2️⃣ **WebFlux로 전환** (Reactive, Non-blocking)
```java
// Blocking
String url = redisTemplate.get(key); // 스레드 대기

// Non-blocking
Mono<String> url = reactiveRedis.get(key); // 이벤트 루프
```
**예상 효과:**
- TPS: 4,587 → **10,000+** (118% 증가)
- P95: 425ms → **<200ms** (53% 개선)

### 3️⃣ **WebFlux + 2-Level Cache** (Phase 4)
```
Caffeine (L1) + Reactive Redis (L2) + Netty
```
**예상 효과:**
- TPS: 4,587 → **10,781** (135% 증가) ✅
- 평균 응답: 179.5ms → **65.97ms** (63% 개선) ✅

---

## 결론

### ✅ 달성한 것
- **완벽한 안정성** (에러율 0%, 성공률 100%)
- Redis 캐싱으로 DB 부하 감소
- 82만 건 요청 무결점 처리

### ❌ 개선 필요
- **10K TPS 목표 미달** (45.9%)
- Tomcat 스레드 풀 병목
- Redis 네트워크 오버헤드

### 💡 교훈
**"전통적인 Blocking 아키텍처(Tomcat + Redis)는 대규모 트래픽(10K TPS)에 한계가 있다."**

→ **WebFlux + 2-Level Cache**로 전환 필요 (Phase 4에서 실증)

---

## 테스트 재현

```bash
# 1. Phase 2 브랜치로 이동
git checkout phase2

# 2. DB 및 Redis 초기화
redis-cli FLUSHALL
mysql -uroot -p<password> bitly -e "TRUNCATE TABLE urls; TRUNCATE TABLE url_clicks;"

# 3. 서버 시작
cd backend
DB_USERNAME=root DB_PASSWORD=<password> ./gradlew bootRun --args='--spring.profiles.active=phase2'

# 4. 10K TPS 테스트 실행
cd ..
k6 run backend/performance-tests/target-10k-test.js
```
