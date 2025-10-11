# Phase 3: Kafka 비동기 처리

## 🎯 목표

**클릭 기록을 비동기로 처리하여 응답 시간 단축**

- Phase 2 병목 해결: 동기 INSERT 제거
- Kafka 비동기 발행으로 즉시 응답
- 진정한 Redis 캐싱 효과 발휘

## 📋 구현 내용

### 1. 아키텍처

```
사용자 요청
    ↓
Spring Boot (Tomcat)
    ↓
Redis 캐시 (조회)
    ↓
Kafka Producer (클릭 이벤트 발행, 비동기)
    ↓
즉시 응답! ⚡
    
    
[백그라운드]
Kafka Consumer → MySQL INSERT
```

**추가된 기능**:
- ✅ Kafka Producer: 클릭 이벤트 비동기 발행
- ✅ Kafka Consumer: 배치 처리로 DB 저장
- ✅ 동기 INSERT 제거 → 응답 즉시 반환

### 2. 리디렉션 처리 개선

#### Phase 2 (동기)
```java
public ShortUrlLookupResult findOriginalUrl(...) {
    // 1. 조회: 1ms (Redis)
    result = urlCacheService.findByShortCode(shortCode);
    
    // 2. 기록: 60ms (MySQL INSERT) ← 병목!
    urlClickRepository.save(click);
    
    return result; // 61ms 후 반환
}
```

#### Phase 3 (비동기)
```java
public ShortUrlLookupResult findOriginalUrl(...) {
    // 1. 조회: 1ms (Redis)
    result = urlCacheService.findByShortCode(shortCode);
    
    // 2. 이벤트 발행: 1ms (Kafka, 비동기) ← 개선!
    eventPublisher.publish(new UrlClickEvent(result.urlId()));
    
    return result; // 2ms 후 즉시 반환! ⚡
}
```

### 3. 설정

```yaml
hikari:
  maximum-pool-size: 50       # Phase 1, 2와 동일

tomcat:
  threads:
    max: 500                  # Phase 1, 2와 동일

redis:
  cache:
    time-to-live: 10분

kafka:
  bootstrap-servers: localhost:9092
  producer:
    acks: 1                   # 빠른 응답
  consumer:
    max-poll-records: 500     # 배치 처리
```

**핵심**: Phase 1, 2 설정 유지, Kafka만 추가

---

## 🚀 테스트 실행

### 사전 준비

```bash
# Redis 확인
redis-cli ping

# Kafka 실행 (Docker)
docker-compose up -d kafka zookeeper
# 또는
# Kafka 직접 실행
```

### 1. 서버 시작

```bash
cd backend

# 기존 서버 종료
lsof -ti:8080 | xargs kill -9

# Phase 3 서버 시작
./gradlew bootRun --args='--spring.profiles.active=phase3'
```

### 2. 표준 테스트 실행 (다른 터미널)

```bash
cd /Users/okestro/Desktop/dev/bitly

# 테스트 실행
k6 run backend/performance-tests/standard-load-test.js
```

### 3. 예상 결과

```
Phase 2 (Redis + 동기 INSERT):
- TPS: 5,447
- P95: 173ms
- 병목: INSERT 60ms

Phase 3 (Redis + Kafka 비동기):
- TPS: 10,000-15,000 예상 (2-3배)
- P95: 50-100ms 예상 (50-70% 개선)
- 병목 제거: INSERT 비동기 처리

기대:
- 조회: 1ms (Redis)
- 발행: 1ms (Kafka)
- 합계: 2ms
→ 30배 빨라질 것으로 예상!
```

---

## 💡 Phase 3의 의미

### Phase 2와의 차이

```
Phase 2:
- 조회: Redis (빠름) ✅
- 기록: MySQL (느림) ❌
- TPS: 5,447
- 병목: 동기 INSERT

Phase 3:
- 조회: Redis (빠름) ✅
- 기록: Kafka (빠름) ✅
- TPS: ???
- 병목: 제거!

핵심: "진정한 비동기 처리"
```

---

**작성일**: 2025-10-11  
**테스트**: standard-load-test.js (500 VU, 7분)

