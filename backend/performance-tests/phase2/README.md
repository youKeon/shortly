# Phase 2: Redis 캐싱

## 목표
- **TPS**: 3,000 (Phase 1 대비 ~3배)
- **동시 사용자**: 500명
- **P95 응답시간**: < 50ms
- **실패율**: < 1%
- **캐시 히트율**: > 70%

## 최적화 내용

### 1. Redis 캐싱 도입

#### 캐싱 전략
- **캐시 대상**: ShortUrl 조회 결과
- **TTL**: 10분
- **캐시 키**: `bitly:shortUrls::${shortCode}`
- **직렬화**: JSON (Jackson)

#### 구현 방식
```java
@Cacheable(value = "shortUrls", key = "#shortCode")
@Transactional(readOnly = true)
public ShortUrlLookupResult findByShortCode(String shortCode) {
    // DB 조회 (캐시 미스 시에만 실행)
    ShortUrl shortUrl = shortUrlRepository.findByShortUrl(shortCode)
            .orElseThrow(() -> new IllegalArgumentException("Short code not found: " + shortCode));

    return ShortUrlLookupResult.of(shortUrl.getId(), shortUrl.getOriginalUrl(), shortUrl.getShortUrl());
}
```

#### 클릭 기록 처리
- 클릭 기록은 캐시와 독립적으로 항상 수행
- 캐시 히트 시에도 클릭 이벤트 누락 방지

### 2. HikariCP Connection Pool 확장

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 100        # Phase 1: 50 → Phase 2: 100
      minimum-idle: 20
      connection-timeout: 3000ms
      max-lifetime: 1800000ms       # 30분
```

#### 설정 근거
- 500 VUs 처리를 위한 충분한 커넥션 수
- 캐시 도입으로 DB 부하 감소 예상 (~70%)
- 실제 DB 접근은 ~150 VUs 수준

### 3. Tomcat 서버 확장

```yaml
server:
  tomcat:
    threads:
      max: 400                      # Phase 1: 200 → Phase 2: 400
      min-spare: 50
    max-connections: 10000
    accept-count: 200
```

#### 설정 근거
- 500 VUs 동시 처리 보장
- 캐시 히트 시 빠른 응답으로 스레드 재사용률 증가

### 4. Redis Connection Pool

```yaml
spring:
  data:
    redis:
      lettuce:
        pool:
          max-active: 50            # 최대 커넥션
          max-idle: 20              # 유휴 커넥션
          min-idle: 5               # 최소 커넥션
          max-wait: 3000ms          # 대기 시간
```
