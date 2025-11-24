# Shortly - URL 단축 서비스

5,000 TPS 고가용성 URL 단축 서비스 - 마이크로서비스 아키텍처(MSA)와 이벤트 기반 아키텍처(EDA)를 적용했습니다.

## Ⅰ.  기술 스택

### 1. Backend
- Language: Java 21
- Framework: Spring Boot 3.5.6
- Build: Gradle
- Database: MySQL
- Cache: Caffeine (L1) + Redis (L2)
- Message: Apache Kafka

### 2. Infrastructure
- Container: Docker Compose
- Monitoring: Prometheus + Grafana
- Testing: K6 + JUnit
- API Gateway: Nginx

### 3. Frontend
- Framework: React 18 + TypeScript
- Build: Vite

## Ⅱ. 주요 기능

### 1. 기능 요구사항
- URL 단축: 긴 URL을 짧은 코드로 단축
- 리디렉션: 단축 URL 클릭 시 원본 URL로 자동 리다이렉트
- 클릭 통계: 실시간 클릭 수 집계 및 조회 (최근 24시간/7일)

### 2. 비기능 요구사항

#### 성능 (Performance)
- **전체 TPS**: 5,000 TPS 처리 (URL 생성 400 / 리디렉션 4,500 / 통계 100)
- **응답 시간**: P95 < 200ms, P99 < 500ms (리디렉션 기준)
- **캐시 히트율**: 95% 이상 유지 (L1 Caffeine 80% + L2 Redis 20%)

#### 가용성 (Availability)
- **서비스 독립성**: 일부 서비스 장애 시에도 나머지 서비스 정상 동작
- **Circuit Breaker**: Kafka 장애 시 Redirect 서비스 격리 (클릭 이벤트 유실 허용)
- **분산 캐시**: Redis 장애 시 L1 캐시(Caffeine)로 fallback

#### 확장성 (Scalability)
- **수평 확장**: MSA 구조로 서비스별 독립 스케일링 가능
- **느슨한 결합**: Kafka 이벤트 기반 비동기 통신

## Ⅲ. 아키텍처

### 1. 마이크로서비스 아키텍처 (MSA)
- URL Service: URL 단축 및 관리
- Redirect Service: 리디렉션
- Click Service: 클릭 통계 수집 및 조회

### 2. Apache Kafka를 통한 서비스 간 비동기 통신
- `UrlCreatedEvent`
  - URL 생성 → L1, L2 캐시에 단축 URL 정보 저장
- `UrlClickedEvent`
  - 클릭 발생 → 통계 수집

### 3. Infrastructure
- Nginx: API Gateway 및 로드 밸런싱
- Redis: 분산 캐시 (L2)
- Caffeine: 로컬 캐시 (L1)
- MySQL: URL/Click 데이터 저장
- Prometheus + Grafana: 모니터링

## Ⅳ. 실행 방법

```bash
# 1. 인프라 실행 (MySQL, Redis, Kafka)
docker-compose -f infra/compose/docker-compose-dev.yml up -d

# 2. 서비스 실행 (별도 터미널)
# Terminal 1: URL Service
./gradlew :shortly-url-service:bootRun

# Terminal 2: Redirect Service
./gradlew :shortly-redirectLookupResult-service:bootRun

# Terminal 3: Click Service
./gradlew :shortly-click-service:bootRun
```

## Ⅴ. 핵심 기술

### 1. 2-Layer Cache (Caffeine + Redis)
- **L1 (Caffeine)**: 로컬 메모리 캐시, 최대 10만 엔트리, TTL 10분
- **L2 (Redis)**: 분산 캐시, TTL 30분, Lettuce 연결 풀 300
- **Cache Warming**: Kafka `UrlCreatedEvent` 구독하여 캐시 사전 적재
- **결과**: 캐시 히트율 100% 달성 (L1 80% + L2 20%)

### 2. Kafka Fire-and-Forget 전략
- **설정**: `acks=0`, `linger.ms=0`, `max.block.ms=100`
- **Circuit Breaker**: Kafka 장애 시 이벤트 유실 허용 (클릭 통계는 best-effort)
- **트레이드오프**: 이벤트 유실 가능성 vs 초저지연 응답 (Redirect P95 < 300ms)

### 3. Base62 Short Code 생성
- **알고리즘**: SHA-256(originalUrl + nanoTime) → Base62 인코딩 → 6자리
- **Collision 처리**: 충돌 시 재시도 (salt 변경)
- **결과**: 56.8억 개 고유 코드 생성 가능

### 4. Distributed Lock (Redis)
- **목적**: 캐시 미스 시 동시 다발적 DB 조회 방지 (Cache Stampede)
- **구현**: Redisson 분산 락 (TTL 5초, Wait Time 3초)
- **결과**: DB 부하 99% 감소
