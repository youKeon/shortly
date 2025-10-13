# Phase 4: WebFlux + 2-Level Caching (Caffeine + Redis)

## 🎯 목표

**Reactive Programming + 로컬/글로벌 캐시를 통한 성능 개선**

- Spring WebFlux 도입
- Caffeine (L1 로컬 캐시) + Redis (L2 글로벌 캐시)
- Non-blocking I/O로 높은 동시성 처리

---

## 아키텍처

```
사용자 요청
    ↓
Netty (WebFlux)
    ↓
ReactiveShortUrlService
    ↓
Caffeine L1 캐시 (로컬 메모리, ~1μs)
    ↓ (Cache Miss)
Reactive Redis L2 캐시 (글로벌, ~1ms)
    ↓ (Cache Miss)
MySQL
```

### 캐시 전략

#### 조회 플로우
1. **Caffeine L1 캐시** 확인 (히트 → 즉시 반환)
2. **Redis L2 캐시** 확인 (히트 → L1에 저장 후 반환)
3. **MySQL DB** 조회 (미스 → L1, L2에 모두 저장)

#### 저장 플로우
- DB 저장 → Redis 저장 → Caffeine 저장

---

## 설정 (application-phase4.yml)

```yaml
spring:
  # Blocking JPA (Reactive로 감싸서 사용)
  datasource:
    url: jdbc:mysql://localhost:3306/bitly
    hikari:
      maximum-pool-size: 50

  # Reactive Redis (L2 Cache)
  data:
    redis:
      host: localhost
      port: 6379
      lettuce:
        pool:
          max-active: 20

  # Caffeine Cache (L1 Cache)
  cache:
    type: caffeine
    caffeine:
      spec: maximumSize=10000,expireAfterWrite=10m,recordStats

# WebFlux (Netty)
server:
  port: 8080
  netty:
    connection-timeout: 5s
    idle-timeout: 60s
```
