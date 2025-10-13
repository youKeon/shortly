# Phase 4: Reactive 아키텍처 + 2-Level Caching

## 목표

**Reactive Programming + 로컬/글로벌 캐시로 10K TPS 달성**

- Spring WebFlux 도입 (Non-blocking I/O)
- Caffeine (L1 로컬 캐시) + Redis (L2 글로벌 캐시)
- Event Loop 기반 높은 동시성 처리

---

## 구현 내용

### 아키텍처

```
사용자 요청
    ↓
Netty (WebFlux, Non-blocking)
    ↓
Reactive Controller
    ↓
[L1] Caffeine Cache (로컬) → 즉시 응답 (~1μs)
    ↓ (Cache Miss)
[L2] Redis (글로벌) → 빠른 응답 (~1-2ms)
    ↓ (Cache Miss)
MySQL (Blocking → boundedElastic)
```

**개선 사항**:
- Tomcat → Netty
- Blocking I/O → Non-blocking I/O
- 2-Level Caching (Caffeine + Redis)
- Reactive Redis

---

### 핵심 구현

#### 1. WebFlux Controller
```java
@RestController
@RequestMapping("/api/v1/urls")
@Profile("phase4")
public class ReactiveShortUrlController {
    
    @GetMapping("/{shortCode}")
    public Mono<ResponseEntity<Void>> redirect(@PathVariable String shortCode) {
        return shortUrlService.findOriginalUrl(ShortUrlLookupCommand.of(shortCode))
            .map(result -> ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(result.originalUrl()))
                .<Void>build())
            .onErrorResume(error -> {
                return Mono.just(ResponseEntity.notFound().build());
            });
    }
}
```

#### 2. 2-Level Caching
```java
@Service
@Profile("phase4")
public class ReactiveShortUrlService {
    
    public Mono<ShortUrlLookupResult> findOriginalUrl(ShortUrlLookupCommand command) {
        String shortCode = command.shortCode();
        Cache caffeineCache = caffeineCacheManager.getCache("shortUrls");
        
        // L1: Caffeine (로컬 메모리 캐시)
        ShortUrlLookupResult cached = caffeineCache.get(shortCode, ...class);
        if (cached != null) {
            return Mono.just(cached); // 초고속 응답 (~1μs)
        }
        
        // L2: Reactive Redis
        return reactiveRedisTemplate.opsForValue()
            .get("shortUrls::" + shortCode)
            .flatMap(redisResult -> {
                caffeineCache.put(shortCode, redisResult); // L1 채움
                return Mono.just(redisResult);
            })
            // L3: DB 조회 (Cache Miss)
            .switchIfEmpty(Mono.fromCallable(() -> 
                shortUrlRepository.findByShortUrl(shortCode)
                    .orElseThrow(...)
            ).subscribeOn(Schedulers.boundedElastic()));
    }
}
```

#### 3. 비동기 클릭 카운트
```java
public Mono<Long> incrementClickCount(Long urlId) {
    return reactiveStringRedisTemplate.opsForValue()
        .increment("url:click:" + urlId)
        .onErrorResume(error -> Mono.empty()); // 실패해도 무시
}
```

---

### 설정 (application-phase4.yml)

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 50

  data:
    redis:
      host: localhost
      port: 6379
      lettuce:
        pool:
          max-active: 20

  cache:
    type: caffeine
    caffeine:
      spec: maximumSize=10000,expireAfterWrite=10m

server:
  port: 8080
  netty:
    connection-timeout: 5s
    idle-timeout: 60s
```

---

## 테스트 실행

```bash
# Phase 4 서버 시작
cd backend
DB_USERNAME=root DB_PASSWORD=<password> ./gradlew bootRun --args='--spring.profiles.active=phase4'

# 10K TPS 테스트
cd ..
k6 run backend/performance-tests/target-10k-test.js
```
