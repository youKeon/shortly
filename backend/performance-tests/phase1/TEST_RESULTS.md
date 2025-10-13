# Phase 1 테스트 결과

## 테스트 결과

| 지표 | 값 |
|------|-----|
| **TPS** | **4,198** |
| **P95** | 430.43ms |
| **평균 응답** | 196.15ms |
| **중간값** | 185.3ms |
| **에러율** | 0% |
| **총 요청** | 757,539 |

---

## 구현 사항

### 아키텍처

```
사용자 요청
    ↓
Spring Boot (Tomcat)
    ↓
JPA (Hibernate) → 모든 요청이 DB 직행
    ↓
MySQL
```

### 핵심 구현

#### ShortUrlService
```java
@Service
public class ShortUrlService {
    
    public ShortUrlLookupResult findOriginalUrl(String shortCode) {
        // 캐시 없이 매번 DB 조회
        ShortUrl shortUrl = shortUrlRepository.findByShortUrl(shortCode)
            .orElseThrow(...);
        
        // 클릭 카운트 동기 저장 (병목)
        urlClickService.incrementClickCount(shortUrl.getId());
        
        return ShortUrlLookupResult.of(...);
    }
}
```

**특징**:
- 캐시 없음 (모든 요청이 DB 직행)
- 클릭 카운트 동기 저장
- Blocking I/O

---

## 성능 분석

### 병목 지점

#### 1. 캐시 완전 부재
- 90% 리디렉션 요청이 매번 DB 조회
- DB가 단일 장애점 (SPOF)

#### 2. DB Connection Pool 병목
- HikariCP: max 20 connections
- 동시 요청 처리 한계

#### 3. 클릭 카운트 동기 저장
- 모든 리디렉션 요청마다 DB INSERT
- 응답 시간 증가

---

## 결론

### 한계
- 10K TPS 목표의 42%만 달성
- 캐시 없어 가장 낮은 성능
- DB 부하 심각

### 개선 방향
- Phase 2: Redis 캐시 추가 필요
- Phase 3: 비동기 처리 필요
- Phase 4: Reactive 아키텍처 필요
