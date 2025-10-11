# Phase 2: Redis 캐싱

## 🎯 목표

**Redis 캐싱 추가로 성능 개선 측정**

- Phase 1 대비 개선율 측정
- 캐시 히트율에 따른 TPS 증가 확인

## 📋 구현 내용

### 1. 아키텍처

```
사용자 요청
    ↓
Spring Boot (Tomcat)
    ↓
Redis 캐시 확인
    ↓ (캐시 미스)
JPA (Hibernate)
    ↓
MySQL
```

**추가된 기능**:
- ✅ Redis 캐싱 (`@Cacheable`)
- ✅ ShortUrl 조회 결과 캐싱
- ❌ 비동기 처리 없음 (여전히)

### 2. 캐싱 전략

```java
@Cacheable(value = "shortUrls", key = "#shortCode")
public ShortUrlLookupResult findByShortCode(String shortCode) {
    // 캐시 미스 시에만 DB 조회
}
```

**설정**:
- TTL: 10분
- 캐시 키: `bitly:shortUrls::${shortCode}`
- 직렬화: JSON

### 3. 설정

```yaml
hikari:
  maximum-pool-size: 50         # Phase 1과 동일
  minimum-idle: 10

tomcat:
  threads:
    max: 500                    # Phase 1과 동일

redis:
  host: localhost
  port: 6379
  cache.ttl: 10분
```

**핵심**: Phase 1 설정 유지, Redis만 추가

---

## 🚀 테스트 실행

### 사전 준비

```bash
# Redis 실행 확인
redis-cli ping
# PONG 응답 확인
```

### 1. 서버 시작

```bash
cd backend

# 기존 서버 종료
lsof -ti:8080 | xargs kill -9

# Phase 2 서버 시작
./gradlew bootRun --args='--spring.profiles.active=phase2'
```

### 2. 표준 테스트 실행 (다른 터미널)

```bash
cd /Users/okestro/Desktop/dev/bitly

# 테스트 실행
k6 run backend/performance-tests/standard-load-test.js
```

### 3. 예상 결과

```
Phase 1 (기본 구현): 5,280 TPS

Phase 2 (Redis 캐싱): 예상
- TPS: 7,000-8,500 (30-60% 개선)
- P95: ~100-150ms (30% 개선)
- 캐시 히트율: ~90% (리디렉션 중심)

개선 근거:
- 리디렉션 90%가 Redis에서 처리
- DB 조회 대폭 감소
- 응답 시간 단축
```

---

## 💡 Phase 2의 의미

### Phase 1과의 차이

```
Phase 1: 모든 요청이 DB 조회
→ TPS: 5,280
→ P95: 177ms

Phase 2: 리디렉션 90%는 Redis에서
→ TPS: ?
→ P95: ?

핵심: Redis 캐싱의 실제 효과 측정
```

---

**작성일**: 2025-10-11  
**테스트**: standard-load-test.js (500 VU, 7분)
