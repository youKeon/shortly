# Phase 3 테스트 결과

## 테스트 결과

| 지표 | Phase 2 | Phase 3 | 개선율 |
|------|---------|---------|--------|
| **TPS** | 5,447 | **6,302** | **+15.7%** ✅ |
| **P95** | 143.14ms | **140.58ms** | **-1.8%** ✅ |
| **평균 응답** | 56.89ms | **54.3ms** | **-4.6%** ✅ |
| **에러율** | 0% | **0%** | ✅ |
| **총 요청** | 2,290,312 | **2,649,820** | +15.7% |

---

## 아키텍처

### 구조

```
사용자 요청
    ↓
Controller (즉시 응답)
    ↓
[리디렉션 90%]                [단축 10%]
    ↓                             ↓
Redis 캐시 조회 (0.5ms)      MySQL INSERT (10-15ms)
    ↓                             ↓
Redis INCR (0.5-1ms)          Redis 저장 (Cache)
    ↓                             ↓
HTTP 302 응답                HTTP 200 응답
    ↓
Scheduler (5분마다)
    ↓
Redis → MySQL Batch INSERT
```

### 개선 사항

#### 1. Redis 클릭 버퍼링
```java
public void incrementClickCount(Long urlId) {
    String key = CLICK_COUNT_PREFIX + urlId;
    redisTemplate.opsForValue().increment(key);  // 0.5-1ms
}
```

#### 2. 스케줄러 배치 저장
```java
@Scheduled(fixedDelay = 300000)  // 5분
public void flushClickCountsToDatabase() {
    // Redis 클릭 카운트를 읽어 MySQL Batch INSERT
}
```

- 5분마다 실행
- 1000개씩 배치 INSERT
- API 응답에 영향 없음

---
