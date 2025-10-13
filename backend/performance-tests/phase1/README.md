# Phase 1: 기본 구현

## 목표

**Spring Boot + MySQL 기본 성능 측정**

- 최소한의 설정으로 기준선 확립
- 캐시 없는 순수 DB 조회 성능 측정
- Phase 2, 3, 4의 비교 기준 제공

---

## 구현 내용

### 아키텍처

```
사용자 요청
    ↓
Spring Boot (Tomcat)
    ↓
JPA (Hibernate)
    ↓
MySQL
```

**특징**:
- 캐시 없음 (모든 요청이 DB 직행)
- Blocking I/O
- Thread Pool: max 200

---

### 핵심 구현

#### ShortUrlService

```java
@Service
@Transactional(readOnly = true)
public class ShortUrlService {
    
    public ShortUrlLookupResult findOriginalUrl(String shortCode) {
        ShortUrl shortUrl = shortUrlRepository.findByShortUrl(shortCode)
            .orElseThrow(() -> new IllegalArgumentException("Short code not found"));
        
        // 클릭 카운트도 동기 저장
        urlClickService.incrementClickCount(shortUrl.getId());
        
        return ShortUrlLookupResult.of(...);
    }
}
```

- 캐시 없이 매번 DB 조회
- 클릭 카운트 동기 저장 (병목)

---

### 설정 (application-phase1.yml)

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20

server:
  tomcat:
    threads:
      max: 200
```

---

## 테스트 실행

```bash
# Phase 1 서버 시작
cd backend
DB_USERNAME=root DB_PASSWORD=<password> ./gradlew bootRun --args='--spring.profiles.active=phase1'

# 10K TPS 테스트
cd ..
k6 run backend/performance-tests/phase1/target-10k-test.js
```
