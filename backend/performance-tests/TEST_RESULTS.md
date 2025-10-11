# Phase 2 테스트 결과

## 테스트 결과

| 지표 | Phase 1 | Phase 2 | 개선율 |
|------|---------|---------|--------|
| **TPS** | 5,280 | **5,447** | +3.2% |
| **P95** | 177ms | **173ms** | -2.3% |
| **P90** | 113ms | **109ms** | -3.5% |
| **평균** | 64ms | **62ms** | -3.1% |
| **에러율** | 0% | **0%** | - |
| **총 요청** | 2,220,229개 | 2,290,212개 | - |

---

## Redis 캐시 성능

| 지표 | 값 |
|------|-----|
| **캐시 히트** | 2,060,760 회 |
| **캐시 미스** | 100 회 |
| **히트율** | **99.99%** ⭐ |
| **리디렉션 요청** | 2,060,860 회 |
| **일치율** | 99.995% |

**캐시는 거의 완벽하게 작동!**

---

## 구현 사항

### 아키텍처

```
사용자 요청
    ↓
Spring Boot (Tomcat)
    ↓
Redis 캐시 (99.99% 히트)
    ↓ (0.01% 미스)
JPA → MySQL
```

### 설정

```yaml
hikari:
  maximum-pool-size: 50       # Phase 1과 동일

tomcat:
  threads:
    max: 500                  # Phase 1과 동일

redis:
  cache:
    time-to-live: 10분        # TTL
```

---

## 핵심 발견

### ✅ Redis 캐싱 성공

```
조회 성능:
- Phase 1: 32ms (MySQL)
- Phase 2: 1ms (Redis)
- 개선: 32배 빨라짐 ⭐
```

### ⚠️ 하지만 전체 개선은 3%

**이유: 클릭 기록이 병목**

```java
public ShortUrlLookupResult findOriginalUrl(...) {
    // 1. 조회: 1ms (Redis) ✅
    result = urlCacheService.findByShortCode(shortCode);
    
    // 2. 기록: 60ms (MySQL INSERT) ❌
    urlClickRepository.save(click);
    
    // 총: 61ms
}
```

**클릭 기록이 전체 시간의 98% 차지!**

---

## 결론

| 항목 | 평가 |
|------|------|
| Redis 캐싱 | ⭐⭐⭐ 99.99% 히트율 |
| 조회 성능 | ⭐⭐⭐ 32배 개선 |
| 전체 TPS | ⭐ 3% 개선 (낮음) |
| 병목 발견 | 클릭 기록 INSERT |

**교훈**: 
- ✅ Redis는 완벽하게 작동
- ❌ 동기 DB 쓰기가 병목
- 💡 Phase 3 필요: Kafka 비동기 처리

---

**작성일**: 2025-10-11  
**테스트**: standard-load-test.js (500 VU, 7분)

