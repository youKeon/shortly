# Phase 2 테스트 결과

## 테스트 결과

| 지표 | Phase 1 | Phase 2 | 개선율 |
|------|---------|---------|--------|
| **TPS** | 4,198 | **4,587** | **+9.3%** |
| **P95** | 430.43ms | **425.18ms** | **-1.2%** |
| **평균 응답** | 196.15ms | **179.5ms** | **-8.5%** |
| **중간값** | 185.3ms | **152.75ms** | **-17.6%** |
| **에러율** | 0% | **0%** | - |
| **총 요청** | 757,539 | **827,486** | +9.2% |

---

## 구현 사항

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

### 핵심 구현

#### 1. Redis 캐싱
```java
@Service
public class UrlCacheService {
    
    public Optional<ShortUrlLookupResult> getShortUrl(String shortCode) {
        String cached = redisTemplate.opsForValue().get("shortUrls::" + shortCode);
        return cached != null ? Optional.of(deserialize(cached)) : Optional.empty();
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
public ShortUrlLookupResult findOriginalUrl(String shortCode) {
    return urlCacheService.getShortUrl(shortCode)
        .orElseGet(() -> {
            // Cache Miss → DB 조회 후 캐싱
            ShortUrl shortUrl = shortUrlRepository.findByShortUrl(shortCode)
                .orElseThrow(...);
            urlCacheService.putShortUrl(shortCode, shortUrl.getOriginalUrl());
            return ShortUrlLookupResult.of(...);
        });
}
```

**특징**:
- Redis 캐시 추가
- TTL: 10분
- 네트워크 기반 글로벌 캐시

---

## 성능 분석

### 개선 효과

#### 1. TPS 9.3% 향상
- Phase 1: 4,198 TPS
- Phase 2: 4,587 TPS
- Redis 캐싱 효과 검증

#### 2. 응답 시간 단축
- 평균: 196ms → 179ms (-8.5%)
- 중간값: 185ms → 152ms (-17.6%)

### 한계

#### 1. 여전히 10K TPS 미달 (45.9%)
- Tomcat Blocking I/O 한계
- 스레드 풀 병목

#### 2. Redis 네트워크 오버헤드
- 모든 리디렉션 요청이 Redis 거침 (~1-2ms)
- 로컬 캐시 없음

#### 3. 클릭 카운트 동기 저장
- 여전히 병목 지점

---

## 결론

### 성과
- Redis 캐싱으로 9.3% TPS 향상
- 캐시 효과 검증

### 한계
- 10K TPS 목표 미달
- Blocking I/O 근본적 한계
- 로컬 캐시 필요

### 개선 방향
- Phase 3: 비동기 처리로 클릭 카운트 병목 해결
- Phase 4: Reactive + 2-Level Cache
