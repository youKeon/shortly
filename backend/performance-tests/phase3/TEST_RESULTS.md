# Phase 3 테스트 결과

## 테스트 결과

| 지표 | Phase 2 | Phase 3 | 개선율 |
|------|---------|---------|--------|
| **TPS** | 4,587 | **4,940** | **+7.7%** |
| **P95** | 425.18ms | **360.56ms** | **-15.2%** |
| **평균 응답** | 179.5ms | **166.87ms** | **-7.0%** |
| **중간값** | 152.75ms | **154.57ms** | +1.2% |
| **에러율** | 0% | **0%** | - |
| **총 요청** | 827,486 | **890,227** | +7.6% |

---

## 구현 사항

### 아키텍처

```
사용자 요청
    ↓
Spring Boot (Tomcat)
    ↓
Redis Cache
    ↓ (Cache Miss)
MySQL
    ↓
Kafka (클릭 이벤트)
    ↓
Redis 버퍼 → 스케줄러 → MySQL Batch INSERT
```

### 핵심 구현

#### 1. Redis 클릭 버퍼링
```java
public void incrementClickCount(Long urlId) {
    String key = CLICK_COUNT_PREFIX + urlId;
    redisTemplate.opsForValue().increment(key);
    // 즉시 반환 (비동기)
}
```

#### 2. 스케줄러 배치 저장
```java
@Scheduled(fixedDelay = 300000)  // 5분
public void flushClickCountsToDatabase() {
    // Redis 클릭 카운트를 읽어 MySQL Batch INSERT
}
```

**특징**:
- 5분마다 실행
- 1000개씩 배치 INSERT
- API 응답에 영향 없음

---

## 성능 분석

### 개선 효과

#### 1. TPS 7.7% 향상
- Phase 2: 4,587 TPS
- Phase 3: 4,940 TPS
- 비동기 처리 효과 검증

#### 2. P95 15% 단축
- Phase 2: 425ms
- Phase 3: 360ms
- 클릭 카운트 병목 제거

#### 3. DB 쓰기 부하 분산
- 클릭 이벤트가 배치로 저장
- 응답 시간 단축

### 한계

#### 1. 여전히 10K TPS 미달 (49.4%)
- Tomcat Blocking I/O 한계
- 스레드 풀 병목

#### 2. Redis 네트워크 오버헤드
- 로컬 캐시 없음

---

## 결론

### 성과
- 비동기 처리로 7.7% TPS 향상
- P95 15% 단축
- DB 쓰기 부하 분산

### 한계
- 10K TPS 목표 미달
- Blocking I/O 근본적 한계

### 개선 방향
- Phase 4: Reactive 아키텍처 + 2-Level Cache 필요
