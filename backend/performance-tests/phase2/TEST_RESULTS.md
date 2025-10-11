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
| **리디렉션 요청** | 2,060,860 회 |
| **캐시 히트** | 2,060,760 회 |
| **캐시 미스** | 100 회 |
| **히트율** | **99.99%**|

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

## 개선이 미비한 이유
### 클릭 기록 저장의 병목으로 예상

```java
public ShortUrlLookupResult findOriginalUrl(...) {
    result = urlCacheService.findByShortCode(shortCode);

    urlClickRepository.save(click); // 병목 발생
}
```
