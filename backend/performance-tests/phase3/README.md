# Phase 3: 비동기 이벤트 처리

## 목표

**클릭 기록 병목 제거로 성능 개선**

- Phase 2 대비 TPS 증가
- Redis 버퍼링 + Kafka 이벤트로 비동기 처리
- DB 쓰기 부하 분산

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
    ↓
Kafka (클릭 이벤트 비동기 처리)
    ↓
Consumer → MySQL Batch INSERT
```

**개선 사항**:
- Kafka 이벤트 스트리밍
- Redis 클릭 버퍼링
- 스케줄러 배치 저장 (5분마다)

---

### 핵심 구현

#### 1. Redis 클릭 버퍼링
```java
@Service
public class UrlClickService {
    
    private static final String CLICK_COUNT_PREFIX = "url:click:";
    
    public void incrementClickCount(Long urlId) {
        String key = CLICK_COUNT_PREFIX + urlId;
        redisTemplate.opsForValue().increment(key);
        // 즉시 반환 (비동기)
    }
}
```

#### 2. 스케줄러 배치 저장
```java
@Component
public class ClickCountScheduler {
    
    @Scheduled(fixedDelay = 300000)  // 5분마다
    @Transactional
    public void flushClickCountsToDatabase() {
        // Redis SCAN으로 url:click:* 키 조회
        Set<String> keys = scanClickCountKeys();
        
        // 클릭 수만큼 UrlClick 엔티티 생성
        List<UrlClick> clicks = createClickEntities(keys);
        
        // MySQL Batch INSERT (1000개씩)
        urlClickRepository.saveAll(clicks);
        
        // Redis 키 삭제
        redisTemplate.delete(keys);
    }
}
```

**특징**:
- 5분마다 자동 실행
- 1000개씩 배치 INSERT
- API 응답에 영향 없음

---

### 설정 (application-phase3.yml)

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
# Phase 3 서버 시작
cd backend
DB_USERNAME=root DB_PASSWORD=<password> ./gradlew bootRun --args='--spring.profiles.active=phase3'

# 10K TPS 테스트
cd ..
k6 run backend/performance-tests/phase3/target-10k-test.js
```
