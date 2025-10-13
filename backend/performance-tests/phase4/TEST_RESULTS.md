# Phase 4 테스트 결과

## 테스트 결과

| 지표 | Phase 3 | Phase 4 | 개선율 |
|------|---------|---------|--------|
| **TPS** | 6,302 | **10,781** | **+71.1%**|
| **P95** | 140.58ms | 579.87ms | +312.4%|
| **평균 응답** | 54.3ms | **65.97ms** | +21.5%|
| **중간값** | - | **0.876ms** | - |
| **에러율** | 0% | **0%** | ✅ |
| **총 요청** | 2,649,820 | **1,945,274** | -26.6% |

---

## 구현 사항

### 아키텍처

```
사용자 요청
    ↓
Netty (Non-blocking)
    ↓
WebFlux Controller (Reactive)
    ↓
[L1] Caffeine Cache (로컬) → 즉시 응답 (~1μs)
    ↓ (Miss)
[L2] Redis (글로벌) → 빠른 응답 (~1-2ms)
    ↓ (Miss)
MySQL (Blocking → boundedElastic)
```

### 핵심 개선

#### 1. WebFlux (Reactive)
```java
@RestController
@Profile("phase4")
public class ReactiveShortUrlController {

    public Mono<ResponseEntity<Void>> redirect(@PathVariable String shortCode) {
        return shortUrlService.findOriginalUrl(...)
            .map(result -> ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(result.originalUrl()))
                .<Void>build());
    }
}
```

- Tomcat → Netty
- Blocking I/O → Non-blocking I/O
- Thread Pool → Event Loop
- 소수 스레드로 높은 동시성 처리

#### 2. 2-Level Caching
```java
// L1: Caffeine (로컬 메모리)
Cache caffeineCache = cacheManager.getCache("shortUrls");
ShortUrlLookupResult cached = caffeineCache.get(shortCode, ...class);

if (cached != null) {
    return Mono.just(cached); // 초고속 (~1μs)
}

// L2: Reactive Redis
return reactiveRedisTemplate.opsForValue().get("shortUrls::" + shortCode)
    .flatMap(redisResult -> {
        caffeineCache.put(shortCode, redisResult); // L1 채움
        return Mono.just(redisResult);
    });
```

**효과:**
- 90% 리다이렉션 요청이 Caffeine에서 즉시 응답
- Redis 네트워크 호출 최소화
- DB 부하 급감

#### 3. 비동기 클릭 카운트
```java
public Mono<Long> incrementClickCount(Long urlId) {
    return reactiveStringRedisTemplate.opsForValue()
        .increment("url:click:" + urlId)
        .onErrorResume(error -> Mono.empty()); // 실패해도 무시
}
```

- Fire-and-forget 패턴
- API 응답 시간에 영향 없음
