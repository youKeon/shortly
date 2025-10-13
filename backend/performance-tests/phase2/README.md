# Phase 2: Redis 캐싱

## 목표

**Redis 캐싱 추가로 성능 개선 측정**

- Phase 1 대비 개선율 측정
- 캐시 히트율에 따른 TPS 증가 확인
- 네트워크 기반 글로벌 캐시 효과 검증

---

## 구현 내용

### 아키텍처

```
사용자 요청
    ↓
Spring Boot (Tomcat)
    ↓
Redis Cache (L1)
    ↓ (Cache Miss)
JPA → MySQL
```

**개선 사항**:
- Redis 캐시 추가
- ShortUrl 조회 결과 캐싱
- TTL: 10분

---

### 핵심 구현

#### 1. Redis 캐싱
```java
@Service
public class UrlCacheService {
    
    private final RedisTemplate<String, String> redisTemplate;
    
    public Optional<ShortUrlLookupResult> getShortUrl(String shortCode) {
        String cached = redisTemplate.opsForValue().get("shortUrls::" + shortCode);
        if (cached != null) {
            return Optional.of(deserialize(cached)); // Cache Hit
        }
        return Optional.empty(); // Cache Miss
    }
    
    public void putShortUrl(String shortCode, String originalUrl) {
        redisTemplate.opsForValue().set(
            "shortUrls::" + shortCode, 
            serialize(originalUrl),
            Duration.ofMinutes(10)
        );
    }
}
```

#### 2. ShortUrlService
```java
@Service
public class ShortUrlService {
    
    public ShortUrlLookupResult findOriginalUrl(String shortCode) {
        // 1. Redis 캐시 조회
        return urlCacheService.getShortUrl(shortCode)
            .orElseGet(() -> {
                // 2. Cache Miss → DB 조회
                ShortUrl shortUrl = shortUrlRepository.findByShortUrl(shortCode)
                    .orElseThrow(...);
                
                // 3. Redis에 캐싱
                urlCacheService.putShortUrl(shortCode, shortUrl.getOriginalUrl());
                
                return ShortUrlLookupResult.of(...);
            });
    }
}
```

---

### 설정 (application-phase2.yml)

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 50

  data:
    redis:
      host: localhost
      port: 6379

  cache:
    type: redis
    redis:
      time-to-live: 600000  # 10분

server:
  tomcat:
    threads:
      max: 500
```

---

## 테스트 실행

```bash
# Redis 실행 확인
redis-cli ping

# Phase 2 서버 시작
cd backend
DB_USERNAME=root DB_PASSWORD=<password> ./gradlew bootRun --args='--spring.profiles.active=phase2'

# 10K TPS 테스트
cd ..
k6 run backend/performance-tests/phase2/target-10k-test.js
```
